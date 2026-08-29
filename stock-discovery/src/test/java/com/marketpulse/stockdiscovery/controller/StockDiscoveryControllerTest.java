package com.marketpulse.stockdiscovery.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.stockdiscovery.model.EquityRecord;
import com.marketpulse.stockdiscovery.model.EquitySearchResult;
import com.marketpulse.stockdiscovery.model.FundamentalsView;
import com.marketpulse.stockdiscovery.service.StockDiscoveryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = StockDiscoveryController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
        })
class StockDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockDiscoveryService stockDiscoveryService;

    @Test
    void searchReturnsMatches() throws Exception {
        when(stockDiscoveryService.search("RELI", 20))
                .thenReturn(List.of(new EquitySearchResult("RELIANCE", "Reliance Industries Limited", "Energy")));

        mockMvc.perform(get("/api/v1/stocks/search").param("q", "RELI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].companyName").value("Reliance Industries Limited"));
    }

    @Test
    void getHistoryReturnsRows() throws Exception {
        EquityRecord record = new EquityRecord(
                "20MICRONS", "EQ", "03-Aug-2026", new BigDecimal("192.46"), new BigDecimal("193.03"),
                new BigDecimal("199.80"), new BigDecimal("192.50"), new BigDecimal("195.00"),
                new BigDecimal("196.03"), new BigDecimal("197.09"), 173167L, new BigDecimal("341.29"),
                3859L, 75484L, new BigDecimal("43.55"));
        when(stockDiscoveryService.getHistory("20MICRONS")).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/stocks/20MICRONS/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("20MICRONS"))
                .andExpect(jsonPath("$[0].closePrice").value(196.03));
    }

    @Test
    void getHistoryReturnsNotFoundWhenNoRows() throws Exception {
        when(stockDiscoveryService.getHistory("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stocks/UNKNOWN/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFundamentalsReturnsStoredData() throws Exception {
        FundamentalsView view = new FundamentalsView(
                "RELIANCE", "Reliance Industries Limited", "Energy", "Oil & Gas Refining & Marketing",
                "Engages in hydrocarbon exploration.", new BigDecimal("17849330434048"),
                new BigDecimal("23.899258"), new BigDecimal("18.442806"), new BigDecimal("1249.8"),
                new BigDecimal("1611.8"), new BigDecimal("0.0046"), OffsetDateTime.now());
        when(stockDiscoveryService.getFundamentals("RELIANCE")).thenReturn(Optional.of(view));

        mockMvc.perform(get("/api/v1/stocks/RELIANCE/fundamentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Reliance Industries Limited"));
    }

    @Test
    void getFundamentalsReturnsNotFoundWhenNeverFetched() throws Exception {
        when(stockDiscoveryService.getFundamentals("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/stocks/UNKNOWN/fundamentals"))
                .andExpect(status().isNotFound());
    }
}
