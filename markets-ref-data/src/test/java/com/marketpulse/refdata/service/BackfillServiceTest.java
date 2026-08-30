package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BackfillServiceTest {

    private BhavcopyService bhavcopyService;
    private EquityPriceRepository equityPriceRepository;
    private BackfillJobRepository jobRepository;
    private BackfillJobDateRepository jobDateRepository;
    private NseProperties properties;
    private BackfillService service;

    /**
     * "Today" is pinned to Wednesday 2026-08-12 so the NOT_FOUND trust window is testable at all.
     * The window only distrusts the last two days, so a test needs a weekday inside it - and on a
     * real Sunday no such weekday exists, which used to make this suite fail one day in seven.
     *
     * <p>Chosen so the trust boundary lands on Monday 2026-08-10: every other date in this class
     * (2026-08-03..10) stays on the trusted side of it, exactly as with the wall clock.
     */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    private static final Clock TEST_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        bhavcopyService = mock(BhavcopyService.class);
        equityPriceRepository = mock(EquityPriceRepository.class);
        jobRepository = mock(BackfillJobRepository.class);
        jobDateRepository = mock(BackfillJobDateRepository.class);

        properties = new NseProperties();
        properties.getBackfill().setDelayMs(0L);
        properties.getBackfill().setMaxConsecutiveFailures(3);

        when(jobRepository.save(any(BackfillJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of());
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of());

        service = new BackfillService(
                bhavcopyService, equityPriceRepository, jobRepository, jobDateRepository, properties, TEST_CLOCK);
    }

    /** Registers a PENDING job with the mocked repository and returns it. */
    private BackfillJob givenJob(LocalDate from, LocalDate to, boolean force) {
        int totalDates = BackfillService.weekdaysBetween(from, to).size();
        BackfillJob job = new BackfillJob(from, to, force, totalDates);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    /** Every per-date row the service recorded, keyed by date. */
    private Map<LocalDate, BackfillJobDate> recordedDates() {
        ArgumentCaptor<BackfillJobDate> captor = ArgumentCaptor.forClass(BackfillJobDate.class);
        verify(jobDateRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(BackfillJobDate::getTradeDate, Function.identity()));
    }

    @Test
    void weekdaysBetweenExcludesWeekends() {
        // 2026-08-07 Fri, 08 Sat, 09 Sun, 10 Mon
        List<LocalDate> dates = BackfillService.weekdaysBetween(
                LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10));

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10));
    }

    @Test
    void weekdaysBetweenIsInclusiveOfBothEnds() {
        List<LocalDate> dates = BackfillService.weekdaysBetween(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10));

        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 10));
    }

    @Test
    void skipsDatesAlreadyPresentInEquityPrice() {
        LocalDate loaded = LocalDate.of(2026, 8, 6);
        LocalDate missing = LocalDate.of(2026, 8, 7);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of(loaded));
        when(bhavcopyService.downloadBhavcopy(missing))
                .thenReturn(DownloadResult.success(missing, "equity_price", 2416));
        BackfillJob job = givenJob(loaded, missing, false);

        service.executeJob(job.getId());

        verify(bhavcopyService, never()).downloadBhavcopy(loaded);
        verify(bhavcopyService).downloadBhavcopy(missing);
        assertThat(recordedDates().get(loaded).getStatus()).isEqualTo(BackfillDateStatus.SKIPPED);
        assertThat(recordedDates().get(missing).getStatus()).isEqualTo(BackfillDateStatus.SUCCESS);
    }

    @Test
    void skipsDatesPreviouslyRecordedAsNotFound() {
        LocalDate holiday = LocalDate.of(2026, 8, 6);
        LocalDate tradingDay = LocalDate.of(2026, 8, 7);
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(holiday));
        when(bhavcopyService.downloadBhavcopy(tradingDay))
                .thenReturn(DownloadResult.success(tradingDay, "equity_price", 2416));
        BackfillJob job = givenJob(holiday, tradingDay, false);

        service.executeJob(job.getId());

        verify(bhavcopyService, never()).downloadBhavcopy(holiday);
        assertThat(recordedDates().get(holiday).getStatus()).isEqualTo(BackfillDateStatus.SKIPPED);
    }

    @Test
    void aRecentNotFoundIsNotTrustedAsAHolidayYet() {
        // Tuesday, the day before the fixed today: inside the trust window, so a recorded
        // NOT_FOUND may only mean "NSE has not published yet" and must be retried rather than
        // cached as a holiday - caching it would leave a silent, permanent gap.
        LocalDate recent = TODAY.minusDays(1);
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(recent));
        when(bhavcopyService.downloadBhavcopy(recent))
                .thenReturn(DownloadResult.success(recent, "equity_price", 2416));
        BackfillJob job = givenJob(recent, recent, false);

        service.executeJob(job.getId());

        verify(bhavcopyService).downloadBhavcopy(recent);
    }

    @Test
    void anOlderNotFoundIsTrustedAsAHoliday() {
        // The other side of the same boundary: two days back is old enough to trust, so the
        // recorded NOT_FOUND is honoured and the date is skipped rather than re-fetched.
        LocalDate settled = TODAY.minusDays(2);
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(settled));
        BackfillJob job = givenJob(settled, settled, false);

        service.executeJob(job.getId());

        verify(bhavcopyService, never()).downloadBhavcopy(settled);
        assertThat(recordedDates().get(settled).getStatus()).isEqualTo(BackfillDateStatus.SKIPPED);
    }

    @Test
    void forceBypassesBothSkipSets() {
        LocalDate loaded = LocalDate.of(2026, 8, 6);
        LocalDate holiday = LocalDate.of(2026, 8, 7);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any())).thenReturn(List.of(loaded));
        when(jobDateRepository.findKnownNonTradingDates(any(), any())).thenReturn(List.of(holiday));
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenReturn(DownloadResult.success(loaded, "equity_price", 10));
        BackfillJob job = givenJob(loaded, holiday, true);

        service.executeJob(job.getId());

        verify(bhavcopyService).downloadBhavcopy(loaded);
        verify(bhavcopyService).downloadBhavcopy(holiday);
    }

    @Test
    void oneFailingDateDoesNotStopTheWalk() {
        LocalDate bad = LocalDate.of(2026, 8, 6);
        LocalDate good = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(bad)).thenReturn(DownloadResult.failure(bad, "boom"));
        when(bhavcopyService.downloadBhavcopy(good))
                .thenReturn(DownloadResult.success(good, "equity_price", 2416));
        BackfillJob job = givenJob(bad, good, false);

        service.executeJob(job.getId());

        verify(bhavcopyService).downloadBhavcopy(good);
        assertThat(recordedDates().get(bad).getStatus()).isEqualTo(BackfillDateStatus.FAILED);
        assertThat(recordedDates().get(bad).getMessage()).isEqualTo("boom");
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
    }

    @Test
    void aThrowingDateIsRecordedFailedAndTheWalkContinues() {
        LocalDate bad = LocalDate.of(2026, 8, 6);
        LocalDate good = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(bad)).thenThrow(new IllegalArgumentException("Mapping for SERIES not found"));
        when(bhavcopyService.downloadBhavcopy(good))
                .thenReturn(DownloadResult.success(good, "equity_price", 2416));
        BackfillJob job = givenJob(bad, good, false);

        service.executeJob(job.getId());

        assertThat(recordedDates().get(bad).getStatus()).isEqualTo(BackfillDateStatus.FAILED);
        assertThat(recordedDates().get(bad).getMessage()).contains("Mapping for SERIES not found");
        verify(bhavcopyService).downloadBhavcopy(good);
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
    }

    @Test
    void abortsJobAfterMaxConsecutiveFailures() {
        // max-consecutive-failures is 3; Mon 3rd through Fri 7th is five weekdays.
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.failure(inv.getArgument(0), "blocked"));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.FAILED);
        assertThat(job.getMessage()).contains("3 consecutive");
        verify(bhavcopyService, org.mockito.Mockito.times(3)).downloadBhavcopy(any());
        verify(bhavcopyService, never()).downloadBhavcopy(LocalDate.of(2026, 8, 6));
    }

    @Test
    void successResetsTheConsecutiveFailureCounter() {
        LocalDate d1 = LocalDate.of(2026, 8, 3);
        LocalDate d2 = LocalDate.of(2026, 8, 4);
        LocalDate d3 = LocalDate.of(2026, 8, 5);
        LocalDate d4 = LocalDate.of(2026, 8, 6);
        LocalDate d5 = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(d1)).thenReturn(DownloadResult.failure(d1, "x"));
        when(bhavcopyService.downloadBhavcopy(d2)).thenReturn(DownloadResult.failure(d2, "x"));
        when(bhavcopyService.downloadBhavcopy(d3)).thenReturn(DownloadResult.success(d3, "equity_price", 5));
        when(bhavcopyService.downloadBhavcopy(d4)).thenReturn(DownloadResult.failure(d4, "x"));
        when(bhavcopyService.downloadBhavcopy(d5)).thenReturn(DownloadResult.failure(d5, "x"));
        BackfillJob job = givenJob(d1, d5, false);

        service.executeJob(job.getId());

        // 2 failures, reset, then 2 more - never 3 in a row, so the job runs to completion.
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        verify(bhavcopyService).downloadBhavcopy(d5);
    }

    @Test
    void notFoundDoesNotAdvanceTheCircuitBreaker() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.notFound(inv.getArgument(0)));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        verify(bhavcopyService, org.mockito.Mockito.times(5)).downloadBhavcopy(any());
        assertThat(recordedDates().get(to).getStatus()).isEqualTo(BackfillDateStatus.NOT_FOUND);
    }

    @Test
    void marksJobRunningThenCompletedAndCountsEveryDate() {
        LocalDate from = LocalDate.of(2026, 8, 6);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.success(inv.getArgument(0), "equity_price", 100));
        BackfillJob job = givenJob(from, to, false);

        service.executeJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getProcessedDates()).isEqualTo(2);
    }

    @Test
    void createJobCountsWeekdaysAndStartsPending() {
        BackfillJob job = service.createJob(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 10), false);

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.PENDING);
        assertThat(job.getTotalDates()).isEqualTo(2);
        verify(jobRepository).save(eq(job));
    }

    @Test
    void notFoundDoesNotResetTheCircuitBreaker() {
        LocalDate d1 = LocalDate.of(2026, 8, 3);
        LocalDate d2 = LocalDate.of(2026, 8, 4);
        LocalDate d3 = LocalDate.of(2026, 8, 5);
        LocalDate d4 = LocalDate.of(2026, 8, 6);
        LocalDate d5 = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(d1)).thenReturn(DownloadResult.failure(d1, "x"));
        when(bhavcopyService.downloadBhavcopy(d2)).thenReturn(DownloadResult.failure(d2, "x"));
        when(bhavcopyService.downloadBhavcopy(d3)).thenReturn(DownloadResult.notFound(d3));
        when(bhavcopyService.downloadBhavcopy(d4)).thenReturn(DownloadResult.failure(d4, "x"));
        BackfillJob job = givenJob(d1, d5, false);

        service.executeJob(job.getId());

        // NOT_FOUND is neither a failure nor a reset, so d1/d2/d4 are 3 consecutive failures.
        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.FAILED);
        verify(bhavcopyService, never()).downloadBhavcopy(d5);
    }

    @Test
    void stopsTheWalkWhenTheThrottleIsInterrupted() {
        properties.getBackfill().setDelayMs(50L);
        LocalDate first = LocalDate.of(2026, 8, 6);
        LocalDate second = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(any()))
                .thenAnswer(inv -> DownloadResult.success(inv.getArgument(0), "equity_price", 10));
        BackfillJob job = givenJob(first, second, false);

        Thread.currentThread().interrupt();
        try {
            service.executeJob(job.getId());
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into other tests
        }

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.INTERRUPTED);
        verify(bhavcopyService).downloadBhavcopy(first);
        verify(bhavcopyService, never()).downloadBhavcopy(second);
    }

    @Test
    void runJobMarksTheJobFailedWhenTheWalkThrows() {
        BackfillJob job = givenJob(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7), false);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        service.runJob(job.getId());

        assertThat(job.getStatus()).isEqualTo(BackfillJobStatus.FAILED);
        assertThat(job.getMessage()).contains("db down");
    }

    @Test
    void runJobSwallowsAFailureInItsOwnRecoveryPath() {
        BackfillJob job = givenJob(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7), false);
        when(equityPriceRepository.findDistinctTradeDatesBetween(any(), any()))
                .thenThrow(new IllegalStateException("db down"));
        // First lookup succeeds (inside executeJob); the recovery lookup then fails too.
        when(jobRepository.findById(job.getId()))
                .thenReturn(Optional.of(job))
                .thenThrow(new IllegalStateException("db still down"));

        assertThatCode(() -> service.runJob(job.getId())).doesNotThrowAnyException();
    }
}
