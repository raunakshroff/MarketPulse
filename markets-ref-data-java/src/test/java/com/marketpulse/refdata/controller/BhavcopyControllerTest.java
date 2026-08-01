package com.marketpulse.refdata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.service.BhavcopyService;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BhavcopyController.class)
class BhavcopyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
    void getEquitiesReturnsNotFoundWhenFileMissing() throws Exception {
        when(bhavcopyService.getEquityRecords(any())).thenThrow(new NoSuchFileException("missing"));

        mockMvc.perform(get("/api/v1/bhavcopy/2026-08-01"))
                .andExpect(status().isNotFound());
    }
}
