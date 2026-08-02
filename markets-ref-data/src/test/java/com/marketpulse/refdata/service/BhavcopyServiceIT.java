package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import com.marketpulse.refdata.repository.EquitySymbolRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the real TimescaleDB migration, hypertable, and upsert behavior end-to-end. */
@SpringBootTest
@Testcontainers
class BhavcopyServiceIT {

    private static final String ARCHIVE_URL = "https://nsearchives.nseindia.com/products/content/";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("timescale/timescaledb:2.17.2-pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private BhavcopyService bhavcopyService;

    @Autowired
    private EquitySymbolRepository equitySymbolRepository;

    @MockBean
    private NseHttpClient nseHttpClient;

    private static byte[] csv(String prevClose) {
        return ("""
                SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
                1018GS2026, GS, 10-Jul-2026, 103.50, 104.15, 104.15, 103.21, 103.21, 103.21, 104.15, 1056, 1.10, 6, 1056, 100.00
                20MICRONS, EQ, 10-Jul-2026, %s, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
                """.formatted(prevClose))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void downloadPersistsAndReDownloadUpsertsRatherThanDuplicates() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 14);
        String downloadUrl = ARCHIVE_URL + "sec_bhavdata_full_14112025.csv";

        when(nseHttpClient.downloadFile(downloadUrl)).thenReturn(csv("193.94"));
        DownloadResult first = bhavcopyService.downloadBhavcopy(date);
        assertThat(first.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(first.rowCount()).isEqualTo(1);

        when(nseHttpClient.downloadFile(downloadUrl)).thenReturn(csv("999.99"));
        DownloadResult second = bhavcopyService.downloadBhavcopy(date);
        assertThat(second.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(second.rowCount()).isEqualTo(1);

        List<EquityRecord> records = bhavcopyService.getEquityRecords(date);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).symbol()).isEqualTo("20MICRONS");
        assertThat(records.get(0).prevClose()).isEqualByComparingTo("999.99");
        assertThat(records.get(0).date()).isEqualTo("14-Nov-2025");

        assertThat(equitySymbolRepository.findById("20MICRONS")).hasValueSatisfying(symbol -> {
            assertThat(symbol.getFirstSeenDate()).isEqualTo(date);
            assertThat(symbol.getLastSeenDate()).isEqualTo(date);
        });
    }
}
