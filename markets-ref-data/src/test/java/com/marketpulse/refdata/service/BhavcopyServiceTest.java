package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BhavcopyServiceTest {

    private static final String ARCHIVE_URL = "https://nsearchives.nseindia.com/products/content/";

    private static final byte[] RAW_CSV = ("""
            SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
            1018GS2026, GS, 10-Jul-2026, 103.50, 104.15, 104.15, 103.21, 103.21, 103.21, 104.15, 1056, 1.10, 6, 1056, 100.00
            20MICRONS, EQ, 10-Jul-2026, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
            """).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private NseHttpClient nseHttpClient;
    private BhavcopyService service;

    @BeforeEach
    void setUp() {
        nseHttpClient = mock(NseHttpClient.class);
        NseProperties properties = new NseProperties();
        properties.setArchiveUrl(ARCHIVE_URL);
        properties.setDataDir(tempDir.toString());
        service = new BhavcopyService(nseHttpClient, properties);
    }

    @Test
    void downloadsFiltersAndSavesEquityRowsOnly() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 14);
        when(nseHttpClient.downloadFile(ARCHIVE_URL + "sec_bhavdata_full_14112025.csv")).thenReturn(RAW_CSV);

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(1);

        Path savedFile = tempDir.resolve("sec_bhavdata_full_14112025.csv");
        assertThat(Files.exists(savedFile)).isTrue();
        String saved = Files.readString(savedFile);
        assertThat(saved).contains("20MICRONS,EQ,10-Jul-2026");
        assertThat(saved).doesNotContain("1018GS2026");
    }

    @Test
    void treats404AsNotFoundRatherThanFailure() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 15);
        when(nseHttpClient.downloadFile(anyString())).thenThrow(new NseHttpException(404, "Not Found"));

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.NOT_FOUND);
    }

    @Test
    void getEquityRecordsReadsBackSavedFile() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 14);
        when(nseHttpClient.downloadFile(ARCHIVE_URL + "sec_bhavdata_full_14112025.csv")).thenReturn(RAW_CSV);
        service.downloadBhavcopy(date);

        List<EquityRecord> records = service.getEquityRecords(date);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).symbol()).isEqualTo("20MICRONS");
        assertThat(records.get(0).series()).isEqualTo("EQ");
    }
}
