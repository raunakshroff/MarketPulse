package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.client.NseHttpClient;
import com.marketpulse.refdata.client.NseHttpException;
import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.EquityPrice;
import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import com.marketpulse.refdata.repository.EquityPriceRepository;
import com.marketpulse.refdata.repository.EquitySymbolRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BhavcopyServiceTest {

    private static final String ARCHIVE_URL = "https://nsearchives.nseindia.com/products/content/";

    private static final byte[] RAW_CSV = ("""
            SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
            1018GS2026, GS, 10-Jul-2026, 103.50, 104.15, 104.15, 103.21, 103.21, 103.21, 104.15, 1056, 1.10, 6, 1056, 100.00
            20MICRONS, EQ, 10-Jul-2026, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
            """).getBytes(StandardCharsets.UTF_8);

    private static final byte[] MISMATCHED_CSV = ("""
            SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
            20MICRONS, EQ, 27-Jun-2019, 193.94, 196.00, 200.88, 196.00, 198.90, 198.34, 198.21, 136532, 270.62, 2842, 65515, 47.99
            """).getBytes(StandardCharsets.UTF_8);

    private NseHttpClient nseHttpClient;
    private EquityPriceRepository equityPriceRepository;
    private EquitySymbolRepository equitySymbolRepository;
    private BhavcopyService service;

    @BeforeEach
    void setUp() {
        nseHttpClient = mock(NseHttpClient.class);
        equityPriceRepository = mock(EquityPriceRepository.class);
        equitySymbolRepository = mock(EquitySymbolRepository.class);
        NseProperties properties = new NseProperties();
        properties.setArchiveUrl(ARCHIVE_URL);
        service = new BhavcopyService(nseHttpClient, properties, equityPriceRepository, equitySymbolRepository);
    }

    @Test
    void downloadsFiltersAndPersistsEquityRowsOnly() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 10);
        when(nseHttpClient.downloadFile(ARCHIVE_URL + "sec_bhavdata_full_10072026.csv")).thenReturn(RAW_CSV);

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(1);

        ArgumentCaptor<List<EquityPrice>> pricesCaptor = ArgumentCaptor.forClass(List.class);
        verify(equityPriceRepository).upsertAll(pricesCaptor.capture());
        List<EquityPrice> savedRows = pricesCaptor.getValue();
        assertThat(savedRows).hasSize(1);
        assertThat(savedRows.get(0).getSymbol()).isEqualTo("20MICRONS");
        assertThat(savedRows.get(0).getTradeDate()).isEqualTo(date);

        verify(equitySymbolRepository).upsertAll(eq(List.of("20MICRONS")), eq(date));
    }

    @Test
    void treats404AsNotFoundRatherThanFailure() throws Exception {
        LocalDate date = LocalDate.of(2025, 11, 15);
        when(nseHttpClient.downloadFile(anyString())).thenThrow(new NseHttpException(404, "Not Found"));

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.NOT_FOUND);
    }

    @Test
    void getEquityRecordsMapsPersistedRowsBackToWireFormat() {
        LocalDate date = LocalDate.of(2025, 11, 14);
        EquityPrice entity = new EquityPrice(
                date, "20MICRONS", "EQ",
                new BigDecimal("193.94"), new BigDecimal("196.00"), new BigDecimal("200.88"),
                new BigDecimal("196.00"), new BigDecimal("198.90"), new BigDecimal("198.34"),
                new BigDecimal("198.21"), 136532L, new BigDecimal("270.62"), 2842L, 65515L,
                new BigDecimal("47.99"));
        when(equityPriceRepository.findByTradeDateOrderBySymbolAsc(date)).thenReturn(List.of(entity));

        List<EquityRecord> records = service.getEquityRecords(date);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).symbol()).isEqualTo("20MICRONS");
        assertThat(records.get(0).series()).isEqualTo("EQ");
        assertThat(records.get(0).date()).isEqualTo("14-Nov-2025");
    }

    @Test
    void rejectsCsvWhoseContentDateDisagreesWithRequestedDate() throws Exception {
        LocalDate requested = LocalDate.of(2019, 9, 30);
        when(nseHttpClient.downloadFile(anyString())).thenReturn(MISMATCHED_CSV);

        DownloadResult result = service.downloadBhavcopy(requested);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.FAILURE);
        assertThat(result.message()).contains("2019-09-30").contains("2019-06-27");
        verify(equityPriceRepository, never()).upsertAll(any());
        verify(equitySymbolRepository, never()).upsertAll(any(), any());
    }

    @Test
    void acceptsCsvWhoseContentDateMatchesRequestedDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 10);
        when(nseHttpClient.downloadFile(anyString())).thenReturn(RAW_CSV);

        DownloadResult result = service.downloadBhavcopy(date);

        assertThat(result.status()).isEqualTo(DownloadResult.Status.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(1);
    }
}
