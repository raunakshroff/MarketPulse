package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.entity.BackfillJobDate;
import com.marketpulse.refdata.model.BackfillDateStatus;
import com.marketpulse.refdata.model.BackfillJobStatus;
import com.marketpulse.refdata.repository.BackfillJobDateRepository;
import com.marketpulse.refdata.repository.BackfillJobRepository;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "nse.scheduler.enabled=false",
        "nse.backfill.delay-ms=0"
})
class BackfillServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> timescaledb = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    /** Two EQ rows dated 2026-08-06 - DATE1 must match the requested date or the guard rejects it. */
    private static byte[] csvFor(LocalDate date) {
        String date1 = String.format("%02d-%s-%d",
                date.getDayOfMonth(),
                date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                date.getYear());
        return ("""
                SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
                20MICRONS, EQ, %s, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
                AARTIIND, EQ, %s, 1785.50, 1788.00, 1801.55, 1780.70, 1783.00, 1782.30, 1784.81, 41969, 749.07, 2541, 32915, 78.43
                """).formatted(date1, date1).getBytes(StandardCharsets.UTF_8);
    }

    @MockBean
    private NseHttpClient nseHttpClient;

    @Autowired
    private BackfillService backfillService;

    @Autowired
    private BackfillJobRepository jobRepository;

    @Autowired
    private BackfillJobDateRepository jobDateRepository;

    @Autowired
    private EquityPriceRepository equityPriceRepository;

    @Test
    void runsARangeThenSkipsEverythingOnASecondRun() throws Exception {
        LocalDate thursday = LocalDate.of(2026, 8, 6);
        LocalDate friday = LocalDate.of(2026, 8, 7);
        when(nseHttpClient.downloadFile(anyString()))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    return url.contains("06082026") ? csvFor(thursday) : csvFor(friday);
                });

        BackfillJob first = backfillService.createJob(thursday, friday, false);
        backfillService.executeJob(first.getId());

        BackfillJob reloadedFirst = jobRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getStatus()).isEqualTo(BackfillJobStatus.COMPLETED);
        assertThat(reloadedFirst.getProcessedDates()).isEqualTo(2);
        assertThat(equityPriceRepository.findDistinctTradeDatesBetween(thursday, friday))
                .containsExactlyInAnyOrder(thursday, friday);

        List<BackfillJobDate> firstDates = jobDateRepository.findByJobIdOrderByTradeDateAsc(first.getId());
        assertThat(firstDates).hasSize(2);
        assertThat(firstDates).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(BackfillDateStatus.SUCCESS));
        assertThat(firstDates.get(0).getRowCount()).isEqualTo(2);

        // Second run over the same range: everything is already loaded, so nothing is fetched.
        BackfillJob second = backfillService.createJob(thursday, friday, false);
        backfillService.executeJob(second.getId());

        List<BackfillJobDate> secondDates = jobDateRepository.findByJobIdOrderByTradeDateAsc(second.getId());
        assertThat(secondDates).hasSize(2);
        assertThat(secondDates).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(BackfillDateStatus.SKIPPED));
    }

    @Test
    void recordsHolidaysAsNotFoundAndSkipsThemNextTime() throws Exception {
        LocalDate holiday = LocalDate.of(2026, 8, 10);
        when(nseHttpClient.downloadFile(anyString())).thenThrow(new NseHttpException(404, "Not Found"));

        BackfillJob first = backfillService.createJob(holiday, holiday, false);
        backfillService.executeJob(first.getId());

        assertThat(jobDateRepository.findByJobIdOrderByTradeDateAsc(first.getId()).get(0).getStatus())
                .isEqualTo(BackfillDateStatus.NOT_FOUND);
        assertThat(jobDateRepository.findKnownNonTradingDates(holiday, holiday)).containsExactly(holiday);

        BackfillJob second = backfillService.createJob(holiday, holiday, false);
        backfillService.executeJob(second.getId());

        assertThat(jobDateRepository.findByJobIdOrderByTradeDateAsc(second.getId()).get(0).getStatus())
                .isEqualTo(BackfillDateStatus.SKIPPED);
    }
}
