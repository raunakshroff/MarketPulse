package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.config.NseProperties;
import com.marketpulse.refdata.entity.BackfillJob;
import com.marketpulse.refdata.model.BackfillErrorResponse;
import com.marketpulse.refdata.model.BackfillJobResponse;
import com.marketpulse.refdata.service.BackfillService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bulk historical backfill over a date range. Utility endpoints - not part of the daily flow. */
@RestController
@RequestMapping("/api/v1/bhavcopy/backfill")
public class BackfillController {

    private final BackfillService backfillService;
    private final NseProperties properties;

    public BackfillController(BackfillService backfillService, NseProperties properties) {
        this.backfillService = backfillService;
        this.properties = properties;
    }

    /** Queues a backfill over [from, to]. Returns 202 immediately; the walk runs in the background. */
    @PostMapping
    public ResponseEntity<Object> start(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean force) {

        LocalDate earliest = properties.getBackfill().getEarliestDate();
        if (from.isAfter(to)) {
            return badRequest("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
        if (to.isAfter(LocalDate.now())) {
            return badRequest("'to' (" + to + ") must not be in the future");
        }
        if (from.isBefore(earliest)) {
            return badRequest("'from' (" + from + ") is before " + earliest
                    + ", the earliest date NSE serves a usable Bhavcopy archive file");
        }
        if (backfillService.findActiveJob().isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new BackfillErrorResponse("A backfill job is already pending or running"));
        }

        BackfillJob job = backfillService.createJob(from, to, force);
        backfillService.runJob(job.getId());
        return ResponseEntity.accepted().body(BackfillJobResponse.summary(job, List.of()));
    }

    /** Progress and per-date breakdown for one job. */
    @GetMapping("/{jobId}")
    public ResponseEntity<BackfillJobResponse> getJob(@PathVariable UUID jobId) {
        return backfillService.getJobDetail(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The 50 most recent jobs, newest first. */
    @GetMapping
    public List<BackfillJobResponse> listJobs() {
        return backfillService.listRecentJobs();
    }

    private static ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(new BackfillErrorResponse(message));
    }
}
