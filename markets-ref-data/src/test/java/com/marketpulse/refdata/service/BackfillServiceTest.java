package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
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
                bhavcopyService, equityPriceRepository, jobRepository, jobDateRepository, properties);
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
}
