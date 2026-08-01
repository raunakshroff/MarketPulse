package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.model.DownloadResult;
import com.marketpulse.refdata.model.EquityRecord;
import com.marketpulse.refdata.service.BhavcopyService;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.List;
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
public class BhavcopyController {

    private final BhavcopyService bhavcopyService;

    public BhavcopyController(BhavcopyService bhavcopyService) {
        this.bhavcopyService = bhavcopyService;
    }

    /** Triggers a download for the given date (defaults to today, or the last weekday if today is a weekend). */
    @PostMapping("/download")
    public ResponseEntity<DownloadResult> download(@RequestParam(required = false) LocalDate date) {
        LocalDate targetDate = date != null ? date : lastWeekday(LocalDate.now());
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
    @GetMapping("/{date}")
    public ResponseEntity<List<EquityRecord>> getEquities(@PathVariable LocalDate date) {
        try {
            return ResponseEntity.ok(bhavcopyService.getEquityRecords(date));
        } catch (NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
