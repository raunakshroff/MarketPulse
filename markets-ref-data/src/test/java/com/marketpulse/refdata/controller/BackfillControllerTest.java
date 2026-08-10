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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BackfillController.class)
class BackfillControllerTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
}
