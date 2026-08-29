package com.marketpulse.refdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.refdata.client.YahooFinanceClient;
import com.marketpulse.refdata.client.YahooFinanceException;
import com.marketpulse.refdata.entity.EquityFundamentals;
import com.marketpulse.refdata.model.FundamentalsResult;
import com.marketpulse.refdata.model.FundamentalsView;
import com.marketpulse.refdata.repository.EquityFundamentalsRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundamentalsServiceTest {

    private static final String QUOTE_SUMMARY_JSON =
            """
            {"price":{"longName":"Reliance Industries Limited"},
            "summaryProfile":{"sector":"Energy","industry":"Oil & Gas Refining & Marketing",
            "longBusinessSummary":"Reliance Industries Limited engages in hydrocarbon exploration."},
            "summaryDetail":{"trailingPE":{"raw":23.899258},"forwardPE":{"raw":18.442806},
            "marketCap":{"raw":17849330434048},"fiftyTwoWeekLow":{"raw":1249.8},
            "fiftyTwoWeekHigh":{"raw":1611.8},"dividendYield":{"raw":0.0046}}}
            """;

    private YahooFinanceClient yahooFinanceClient;
    private EquityFundamentalsRepository equityFundamentalsRepository;
    private FundamentalsService service;

    @BeforeEach
    void setUp() {
        yahooFinanceClient = mock(YahooFinanceClient.class);
        equityFundamentalsRepository = mock(EquityFundamentalsRepository.class);
        service = new FundamentalsService(yahooFinanceClient, equityFundamentalsRepository);
    }

    @Test
    void refreshFetchesAndUpsertsFundamentals() throws Exception {
        JsonNode result = new ObjectMapper().readTree(QUOTE_SUMMARY_JSON);
        when(yahooFinanceClient.getFundamentals("RELIANCE")).thenReturn(result);

        List<FundamentalsResult> results = service.refresh(List.of("RELIANCE"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(FundamentalsResult.Status.SUCCESS);
        assertThat(results.get(0).symbol()).isEqualTo("RELIANCE");

        ArgumentCaptor<EquityFundamentals> captor = ArgumentCaptor.forClass(EquityFundamentals.class);
        verify(equityFundamentalsRepository).upsert(captor.capture());
        EquityFundamentals saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("RELIANCE");
        assertThat(saved.getCompanyName()).isEqualTo("Reliance Industries Limited");
        assertThat(saved.getSector()).isEqualTo("Energy");
        assertThat(saved.getMarketCap()).isEqualByComparingTo("17849330434048");
        assertThat(saved.getTrailingPe()).isEqualByComparingTo("23.899258");
    }

    @Test
    void refreshTreats404AsNotFoundRatherThanFailure() throws Exception {
        when(yahooFinanceClient.getFundamentals("FAKE")).thenThrow(new YahooFinanceException(404, "Not Found"));

        List<FundamentalsResult> results = service.refresh(List.of("FAKE"));

        assertThat(results.get(0).status()).isEqualTo(FundamentalsResult.Status.NOT_FOUND);
    }

    @Test
    void refreshIsolatesFailuresPerSymbol() throws Exception {
        when(yahooFinanceClient.getFundamentals("GOOD")).thenReturn(new ObjectMapper().readTree(QUOTE_SUMMARY_JSON));
        when(yahooFinanceClient.getFundamentals("BAD")).thenThrow(new YahooFinanceException(500, "Server Error"));

        List<FundamentalsResult> results = service.refresh(List.of("GOOD", "BAD"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(FundamentalsResult.Status.SUCCESS);
        assertThat(results.get(1).status()).isEqualTo(FundamentalsResult.Status.FAILURE);
        verify(equityFundamentalsRepository).upsert(argThatSymbol("GOOD"));
    }

    @Test
    void getFundamentalsReturnsEmptyWhenNeverFetched() {
        when(equityFundamentalsRepository.findById(anyString())).thenReturn(Optional.empty());

        Optional<FundamentalsView> view = service.getFundamentals("UNKNOWN");

        assertThat(view).isEmpty();
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

    private static EquityFundamentals argThatSymbol(String symbol) {
        return org.mockito.ArgumentMatchers.argThat(e -> e.getSymbol().equals(symbol));
    }
}
