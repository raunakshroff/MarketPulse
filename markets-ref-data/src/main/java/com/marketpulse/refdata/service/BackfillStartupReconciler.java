package com.marketpulse.refdata.service;

import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Reconciles jobs orphaned by a restart. A PENDING or RUNNING job cannot still be live -
 * the JVM that owned it is gone - so leaving that status in place would report a lie.
 *
 * <p>Deliberately does NOT auto-resume: a restart loop must never silently generate an
 * hour and a half of NSE traffic nobody asked for. Recovery is re-POSTing the same range,
 * which is cheap because the skip sets drop everything already loaded.
 */
@Component
public class BackfillStartupReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillStartupReconciler.class);

    private final BackfillJobRepository jobRepository;

    public BackfillStartupReconciler(BackfillJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<BackfillJob> orphaned = jobRepository.findByStatusIn(
                List.of(BackfillJobStatus.PENDING, BackfillJobStatus.RUNNING));

        for (BackfillJob job : orphaned) {
            job.markInterrupted("Interrupted by an application restart after "
                    + job.getProcessedDates() + "/" + job.getTotalDates()
                    + " dates. Re-POST the same range to resume; loaded dates are skipped.");
            jobRepository.save(job);
            log.warn("Backfill job {} was interrupted by a restart at {}/{} dates - "
                            + "re-POST {}..{} to resume",
                    job.getId(), job.getProcessedDates(), job.getTotalDates(),
                    job.getFromDate(), job.getToDate());
        }
    }
}
