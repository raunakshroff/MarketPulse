package com.marketpulse.refdata.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.model.FundamentalsResult;
import com.marketpulse.refdata.model.FundamentalsView;
import com.marketpulse.refdata.service.FundamentalsService;
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
        controllers = FundamentalsController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
        })
class FundamentalsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundamentalsService fundamentalsService;

    @Test
    void refreshReturnsPerSymbolResults() throws Exception {
        when(fundamentalsService.refresh(eq(List.of("RELIANCE", "FAKE"))))
                .thenReturn(List.of(
                        FundamentalsResult.success("RELIANCE"),
                        FundamentalsResult.notFound("FAKE")));

        mockMvc.perform(post("/api/v1/fundamentals/refresh").param("symbols", "RELIANCE", "FAKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].symbol").value("FAKE"))
                .andExpect(jsonPath("$[1].status").value("NOT_FOUND"));
    }

    @Test
    void getFundamentalsReturnsStoredData() throws Exception {
        FundamentalsView view = new FundamentalsView(
                "RELIANCE", "Reliance Industries Limited", "Energy", "Oil & Gas Refining & Marketing",
                "Engages in hydrocarbon exploration.", new BigDecimal("17849330434048"),
                new BigDecimal("23.899258"), new BigDecimal("18.442806"), new BigDecimal("1249.8"),
                new BigDecimal("1611.8"), new BigDecimal("0.0046"), OffsetDateTime.now());
        when(fundamentalsService.getFundamentals("RELIANCE")).thenReturn(Optional.of(view));

        mockMvc.perform(get("/api/v1/fundamentals/RELIANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.companyName").value("Reliance Industries Limited"))
                .andExpect(jsonPath("$.sector").value("Energy"));
    }

    @Test
    void getFundamentalsReturnsNotFoundWhenNeverFetched() throws Exception {
        when(fundamentalsService.getFundamentals("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fundamentals/UNKNOWN"))
                .andExpect(status().isNotFound());
    }
}
