package com.marketpulse.refdata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.service.BackfillService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = BackfillController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
        })
class BackfillControllerTest {

    /**
     * "Today" for this slice. Pinned so the future-date guard can be tested at all - with the wall
     * clock there is no date that is reliably "tomorrow" from the test's point of view.
     */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @TestConfiguration
    static class Config {
        // RefDataApplication's class-level @EnableConfigurationProperties(NseProperties.class)
        // is picked up by @WebMvcTest too (it isn't filtered by slice testing), so a second,
        // YAML-bound NseProperties bean already exists alongside this one. @Primary breaks the tie.
        @Bean
        @Primary
        NseProperties nseProperties() {
            return new NseProperties();
        }

        // ClockConfig is a plain @Configuration, so @WebMvcTest does not load it - the controller
        // needs a Clock supplied here or the slice cannot construct it.
        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackfillService backfillService;

    @BeforeEach
    void setUp() {
        when(backfillService.findActiveJob()).thenReturn(Optional.empty());
    }

    @Test
    void startReturnsAcceptedWithAJobId() throws Exception {
        BackfillJob job = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        when(backfillService.createJob(any(), any(), anyBoolean())).thenReturn(job);

        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-07"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalDates").value(5));

        verify(backfillService).runJob(job.getId());
    }

    @Test
    void rejectsRangeWhereFromIsAfterTo() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-07")
                        .param("to", "2026-08-03"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsRangeEndingInTheFuture() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2099-01-01"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsRangeStartingBeforeTheArchiveExists() throws Exception {
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2015-01-01")
                        .param("to", "2026-08-07"))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void rejectsASecondJobWhileOneIsActive() throws Exception {
        BackfillJob running = new BackfillJob(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), false, 5);
        when(backfillService.findActiveJob()).thenReturn(Optional.of(running));

        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", "2026-08-03")
                        .param("to", "2026-08-07"))
                .andExpect(status().isConflict());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }

    @Test
    void getJobReturnsNotFoundForAnUnknownId() throws Exception {
        when(backfillService.getJobDetail(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bhavcopy/backfill/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJobsReturnsOk() throws Exception {
        when(backfillService.listRecentJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bhavcopy/backfill"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsARangeEndingToday() throws Exception {
        BackfillJob job = new BackfillJob(TODAY, TODAY, false, 1);
        when(backfillService.createJob(any(), any(), anyBoolean())).thenReturn(job);

        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsARangeEndingTomorrow() throws Exception {
        // The exact boundary. rejectsRangeEndingInTheFuture uses a far-future date, which passes
        // under any clock and so never pins where "future" actually starts; the fixed clock does.
        mockMvc.perform(post("/api/v1/bhavcopy/backfill")
                        .param("from", TODAY.toString())
                        .param("to", TODAY.plusDays(1).toString()))
                .andExpect(status().isBadRequest());

        verify(backfillService, never()).createJob(any(), any(), anyBoolean());
    }
}
