package com.marketpulse.refdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.service.BhavcopyService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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

    /**
     * "Today" for this slice, pinned to Sunday 2026-08-09 so the weekend rollback in download()
     * can be exercised end to end. The lastWeekday tests below cover the helper in isolation;
     * only a fixed clock can prove the controller actually applies it when date is omitted.
     */
    private static final LocalDate TODAY_SUNDAY = LocalDate.of(2026, 8, 9);

    @TestConfiguration
    static class Config {
        // ClockConfig is a plain @Configuration, so @WebMvcTest does not load it - the controller
        // needs a Clock supplied here or the slice cannot construct it.
        @Bean
        Clock clock() {
            return Clock.fixed(TODAY_SUNDAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

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

    @Test
    void omittingTheDateOnAWeekendDownloadsThePrecedingFriday() throws Exception {
        LocalDate friday = LocalDate.of(2026, 8, 7);
        when(bhavcopyService.downloadBhavcopy(friday))
                .thenReturn(DownloadResult.success(friday, "equity_price", 2416));

        mockMvc.perform(post("/api/v1/bhavcopy/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // The clock says Sunday, so the controller must resolve to Friday rather than fetching a
        // weekend date NSE publishes nothing for.
        verify(bhavcopyService).downloadBhavcopy(friday);
    }
}
