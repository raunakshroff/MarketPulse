package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import com.marketpulse.refdata.service.BhavcopyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bhavcopy")
@Tag(name = "Bhavcopy", description = "NSE daily EOD equity price ingestion and retrieval")
public class BhavcopyController {

    private final BhavcopyService bhavcopyService;
    private final Clock clock;

    public BhavcopyController(BhavcopyService bhavcopyService, Clock clock) {
        this.bhavcopyService = bhavcopyService;
        this.clock = clock;
    }

    /** Triggers a download for the given date (defaults to today, or the last weekday if today is a weekend). */
    @Operation(
            summary = "Download and store one day's Bhavcopy",
            description = """
                    Fetches the NSE Bhavcopy CSV for the given date, filters it to equity rows \
                    (SERIES = EQ) and upserts them into TimescaleDB. Re-running for a date that \
                    already has data overwrites rather than duplicates, so this is safe to retry.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Downloaded and stored"),
            @ApiResponse(
                    responseCode = "404",
                    description = "NSE has no file for that date - a market holiday, or not published yet. "
                            + "Expected for non-trading days, not an error."),
            @ApiResponse(responseCode = "502", description = "NSE was reachable but the download or parse failed")
    })
    @PostMapping("/download")
    public ResponseEntity<DownloadResult> download(
            @Parameter(description = "Trade date to download. Defaults to today, or the preceding Friday on a weekend.",
                    example = "2026-08-28")
            @RequestParam(required = false) LocalDate date) {
        LocalDate targetDate = date != null ? date : lastWeekday(LocalDate.now(clock));
        DownloadResult result = bhavcopyService.downloadBhavcopy(targetDate);
        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(result);
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            case FAILURE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
        };
    }

    /** Rolls a Saturday/Sunday back to the preceding Friday; any other day is returned unchanged. */
    static LocalDate lastWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.minusDays(2);
            default -> date;
        };
    }

    /** Returns the previously downloaded, equity-only rows for the given date. */
    @Operation(
            summary = "Get stored equity rows for a date",
            description = "Reads back the equity-only rows already stored for that trade date. Does not trigger a download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rows found"),
            @ApiResponse(responseCode = "404", description = "No data stored for that date"),
            @ApiResponse(responseCode = "500", description = "Database read failed")
    })
    @GetMapping("/{date}")
    public ResponseEntity<List<EquityRecord>> getEquities(
            @Parameter(description = "Trade date in ISO form", example = "2026-08-28")
            @PathVariable LocalDate date) {
        try {
            List<EquityRecord> records = bhavcopyService.getEquityRecords(date);
            if (records.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(records);
        } catch (DataAccessException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
