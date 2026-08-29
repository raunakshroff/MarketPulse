package com.marketpulse.stockdiscovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketpulse.stockdiscovery.entity.EquityFundamentals;
import com.marketpulse.stockdiscovery.entity.EquityPrice;
import com.marketpulse.stockdiscovery.model.EquityRecord;
import com.marketpulse.stockdiscovery.model.EquitySearchResult;
import com.marketpulse.stockdiscovery.model.FundamentalsView;
import com.marketpulse.stockdiscovery.repository.EquityFundamentalsRepository;
import com.marketpulse.stockdiscovery.repository.EquityPriceRepository;
import com.marketpulse.stockdiscovery.repository.EquitySearchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockDiscoveryServiceTest {

    private EquitySearchRepository equitySearchRepository;
    private EquityPriceRepository equityPriceRepository;
    private EquityFundamentalsRepository equityFundamentalsRepository;
    private StockDiscoveryService service;

    @BeforeEach
    void setUp() {
        equitySearchRepository = mock(EquitySearchRepository.class);
        equityPriceRepository = mock(EquityPriceRepository.class);
        equityFundamentalsRepository = mock(EquityFundamentalsRepository.class);
        service = new StockDiscoveryService(
                equitySearchRepository, equityPriceRepository, equityFundamentalsRepository);
    }

    @Test
    void searchDelegatesToRepository() {
        List<EquitySearchResult> expected =
                List.of(new EquitySearchResult("RELIANCE", "Reliance Industries Limited", "Energy"));
        when(equitySearchRepository.search("RELI", 20)).thenReturn(expected);

        List<EquitySearchResult> results = service.search("RELI", 20);

        assertThat(results).isEqualTo(expected);
    }

    @Test
    void getHistoryMapsPersistedRowsToWireFormat() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        EquityPrice entity = new EquityPrice(
                date, "20MICRONS", "EQ",
                new BigDecimal("192.46"), new BigDecimal("193.03"), new BigDecimal("199.80"),
                new BigDecimal("192.50"), new BigDecimal("195.00"), new BigDecimal("196.03"),
                new BigDecimal("197.09"), 173167L, new BigDecimal("341.29"), 3859L, 75484L,
                new BigDecimal("43.55"));
        when(equityPriceRepository.findBySymbolOrderByTradeDateAsc("20MICRONS")).thenReturn(List.of(entity));

        List<EquityRecord> history = service.getHistory("20MICRONS");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).symbol()).isEqualTo("20MICRONS");
        assertThat(history.get(0).date()).isEqualTo("03-Aug-2026");
        assertThat(history.get(0).closePrice()).isEqualByComparingTo("196.03");
    }

    @Test
    void getHistoryReturnsEmptyListWhenSymbolUnknown() {
        when(equityPriceRepository.findBySymbolOrderByTradeDateAsc("UNKNOWN")).thenReturn(List.of());

        assertThat(service.getHistory("UNKNOWN")).isEmpty();
    }

    @Test
    void getFundamentalsReturnsEmptyWhenNeverFetched() {
        when(equityFundamentalsRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.getFundamentals("UNKNOWN")).isEmpty();
    }

    @Test
    void getFundamentalsMapsStoredEntityToView() {
        EquityFundamentals entity = new EquityFundamentals(
                "RELIANCE", "Reliance Industries Limited", "Energy", "Oil & Gas Refining & Marketing",
                "Engages in hydrocarbon exploration.", new BigDecimal("17849330434048"),
                new BigDecimal("23.899258"), new BigDecimal("18.442806"), new BigDecimal("1249.8"),
                new BigDecimal("1611.8"), new BigDecimal("0.0046"), OffsetDateTime.now());
        when(equityFundamentalsRepository.findById("RELIANCE")).thenReturn(Optional.of(entity));

        Optional<FundamentalsView> view = service.getFundamentals("RELIANCE");

        assertThat(view).isPresent();
        assertThat(view.get().symbol()).isEqualTo("RELIANCE");
        assertThat(view.get().companyName()).isEqualTo("Reliance Industries Limited");
    }
}
