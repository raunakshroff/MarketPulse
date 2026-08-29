package com.marketpulse.refdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.service.BhavcopyService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = BhavcopyController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
        })
class BhavcopyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BhavcopyService bhavcopyService;

    @Test
    void downloadReturnsOkOnSuccess() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 31);
        when(bhavcopyService.downloadBhavcopy(date))
                .thenReturn(DownloadResult.success(date, "/data/sec_bhavdata_full_31072026.csv", 2410));

        mockMvc.perform(post("/api/v1/bhavcopy/download").param("date", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.rowCount").value(2410));
    }

    @Test
    void downloadReturnsNotFoundWhenBhavcopyMissing() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(bhavcopyService.downloadBhavcopy(date)).thenReturn(DownloadResult.notFound(date));

        mockMvc.perform(post("/api/v1/bhavcopy/download").param("date", "2026-08-01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEquitiesReturnsNotFoundWhenNoRowsPersisted() throws Exception {
        when(bhavcopyService.getEquityRecords(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bhavcopy/2026-08-01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEquitiesReturnsInternalServerErrorOnDbFailure() throws Exception {
        when(bhavcopyService.getEquityRecords(any()))
                .thenThrow(new DataAccessResourceFailureException("db unavailable"));

        mockMvc.perform(get("/api/v1/bhavcopy/2026-08-01"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void lastWeekdayRollsSaturdayBackToFriday() {
        LocalDate saturday = LocalDate.of(2026, 8, 1);
        assertThat(BhavcopyController.lastWeekday(saturday)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void lastWeekdayRollsSundayBackToFriday() {
        LocalDate sunday = LocalDate.of(2026, 8, 2);
        assertThat(BhavcopyController.lastWeekday(sunday)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void lastWeekdayLeavesWeekdaysUnchanged() {
        LocalDate wednesday = LocalDate.of(2026, 7, 29);
        assertThat(BhavcopyController.lastWeekday(wednesday)).isEqualTo(wednesday);
    }
}
