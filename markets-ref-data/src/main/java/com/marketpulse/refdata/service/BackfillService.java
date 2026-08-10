package com.marketpulse.refdata.service;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks a date range and pulls each missing trading day's Bhavcopy.
 *
 * <p>Deliberately a separate bean from {@link BhavcopyService}: calling
 * {@code downloadBhavcopy} across a bean boundary means Spring's {@code @Transactional}
 * proxy actually applies, so every date commits in its own transaction and a failure late
 * in a multi-year run cannot roll back the days already loaded.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private final BhavcopyService bhavcopyService;
    private final EquityPriceRepository equityPriceRepository;
    private final BackfillJobRepository jobRepository;
    private final BackfillJobDateRepository jobDateRepository;
    private final NseProperties properties;

    public BackfillService(
            BhavcopyService bhavcopyService,
            EquityPriceRepository equityPriceRepository,
            BackfillJobRepository jobRepository,
            BackfillJobDateRepository jobDateRepository,
            NseProperties properties) {
        this.bhavcopyService = bhavcopyService;
        this.equityPriceRepository = equityPriceRepository;
        this.jobRepository = jobRepository;
        this.jobDateRepository = jobDateRepository;
        this.properties = properties;
    }

    /** Every weekday in [from, to] inclusive. Weekends are never fetched - NSE publishes no file. */
    static List<LocalDate> weekdaysBetween(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
        }
        return dates;
    }

    /** Persists a new PENDING job. Callers validate the range before calling this. */
    @Transactional
    public BackfillJob createJob(LocalDate from, LocalDate to, boolean force) {
        BackfillJob job = new BackfillJob(from, to, force, weekdaysBetween(from, to).size());
        jobRepository.save(job);
        log.info("Created backfill job {} for {}..{} ({} weekdays, force={})",
                job.getId(), from, to, job.getTotalDates(), force);
        return job;
    }

    /** The oldest job still PENDING or RUNNING, if any. */
    public Optional<BackfillJob> findActiveJob() {
        return jobRepository.findFirstByStatusInOrderByCreatedAtAsc(
                List.of(BackfillJobStatus.PENDING, BackfillJobStatus.RUNNING));
    }

    /**
     * Async entry point. Returns immediately; the walk runs on the single backfill thread.
     * Kept as a thin wrapper so {@code executeJob} stays synchronously testable.
     */
    @Async("backfillExecutor")
    public void runJob(UUID jobId) {
        try {
            executeJob(jobId);
        } catch (RuntimeException e) {
            log.error("Backfill job {} threw unexpectedly: {}", jobId, e.getMessage(), e);
            markFailedQuietly(jobId, e);
        }
    }

    /**
     * Best-effort attempt to record the failure. The recovery write can itself fail - usually the
     * same outage that killed the walk - and if that escaped, the job would stay RUNNING forever,
     * which is the exact wedge this catch exists to prevent. A job left RUNNING is still relabelled
     * INTERRUPTED by BackfillStartupReconciler on the next restart.
     */
    private void markFailedQuietly(UUID jobId, RuntimeException cause) {
        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.markFailed("Unexpected error: " + cause.getMessage());
                jobRepository.save(job);
            });
        } catch (RuntimeException e) {
            log.error("Backfill job {} could not be marked FAILED after an unexpected error; it may "
                    + "stay RUNNING until the next restart reconciles it: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * Runs the whole range synchronously. Package-private so it can be unit tested without
     * any async machinery; {@code runJob} is the public async entry point.
     */
    void executeJob(UUID jobId) {
        BackfillJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Backfill job {} vanished before it could run", jobId);
            return;
        }

        job.markRunning();
        jobRepository.save(job);
        log.info("Backfill job {} started: {}..{}", jobId, job.getFromDate(), job.getToDate());

        Set<LocalDate> skip = skipSet(job);
        int maxConsecutiveFailures = Math.max(1, properties.getBackfill().getMaxConsecutiveFailures());
        int consecutiveFailures = 0;

        for (LocalDate date : weekdaysBetween(job.getFromDate(), job.getToDate())) {
            if (skip.contains(date)) {
                record(job, date, BackfillDateStatus.SKIPPED, 0, "already loaded or known non-trading day");
                continue;
            }

            DownloadResult result = bhavcopyService.downloadBhavcopy(date);
            switch (result.status()) {
                case SUCCESS -> {
                    record(job, date, BackfillDateStatus.SUCCESS, result.rowCount(), null);
                    consecutiveFailures = 0;
                }
                case NOT_FOUND -> record(job, date, BackfillDateStatus.NOT_FOUND, 0, result.message());
                case FAILURE -> {
                    record(job, date, BackfillDateStatus.FAILED, 0, result.message());
                    consecutiveFailures++;
                }
            }

            if (consecutiveFailures >= maxConsecutiveFailures) {
                String message = "Aborted after " + consecutiveFailures
                        + " consecutive failures, last at " + date + ": " + result.message();
                job.markFailed(message);
                jobRepository.save(job);
                log.error("Backfill job {} {}", jobId, message);
                return;
            }

            if (!sleepBetweenDates()) {
                String message = "Interrupted after " + date + "; "
                        + job.getProcessedDates() + "/" + job.getTotalDates() + " dates processed";
                job.markInterrupted(message);
                jobRepository.save(job);
                log.warn("Backfill job {} {}", jobId, message);
                return;
            }
        }

        job.markCompleted();
        jobRepository.save(job);
        log.info("Backfill job {} completed: {}/{} dates processed",
                jobId, job.getProcessedDates(), job.getTotalDates());
    }

    /** Dates we must not fetch: already loaded, or already known to be non-trading days. */
    private Set<LocalDate> skipSet(BackfillJob job) {
        if (job.isForce()) {
            return Set.of();
        }
        Set<LocalDate> skip = new HashSet<>(
                equityPriceRepository.findDistinctTradeDatesBetween(job.getFromDate(), job.getToDate()));
        skip.addAll(jobDateRepository.findKnownNonTradingDates(job.getFromDate(), job.getToDate()));
        return skip;
    }

    private void record(BackfillJob job, LocalDate date, BackfillDateStatus status, long rowCount, String message) {
        jobDateRepository.save(new BackfillJobDate(job.getId(), date, status, rowCount, message));
        job.incrementProcessed();
        jobRepository.save(job);
    }

    /** Returns false if the wait was interrupted, meaning the walk must stop. */
    private boolean sleepBetweenDates() {
        long delayMs = properties.getBackfill().getDelayMs();
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
