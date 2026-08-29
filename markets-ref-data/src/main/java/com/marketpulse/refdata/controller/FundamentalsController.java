package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.model.FundamentalsResult;
import com.marketpulse.refdata.model.FundamentalsView;
import com.marketpulse.refdata.service.FundamentalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fundamentals")
@Tag(name = "Fundamentals", description = "Company fundamentals from Yahoo Finance (market cap, P/E, sector, description)")
public class FundamentalsController {

    private final FundamentalsService fundamentalsService;

    public FundamentalsController(FundamentalsService fundamentalsService) {
        this.fundamentalsService = fundamentalsService;
    }

    /** Fetches and stores fundamentals for one or more symbols (comma-separated), e.g. ?symbols=RELIANCE,TCS. */
    @Operation(
            summary = "Fetch and store fundamentals for one or more symbols",
            description = """
                    On-demand only - there is no scheduled batch refresh. Errors are isolated per \
                    symbol: one unknown or failing symbol does not fail the rest of the batch, so \
                    the 200 response may contain a mix of SUCCESS, NOT_FOUND and FAILURE entries. \
                    Note that Yahoo's unofficial API rate-limits aggressively, so large batches may \
                    return FAILURE entries under throttling.""")
    @ApiResponse(responseCode = "200", description = "Per-symbol results (check each entry's status)")
    @PostMapping("/refresh")
    public ResponseEntity<List<FundamentalsResult>> refresh(
            @Parameter(description = "Comma-separated NSE symbols", example = "RELIANCE,TCS")
            @RequestParam List<String> symbols) {
        return ResponseEntity.ok(fundamentalsService.refresh(symbols));
    }

    /** Returns the previously fetched fundamentals for a symbol. */
    @Operation(
            summary = "Get stored fundamentals for a symbol",
            description = "Reads back fundamentals already stored for that symbol. Does not fetch from Yahoo Finance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fundamentals found"),
            @ApiResponse(responseCode = "404", description = "Never refreshed for this symbol")
    })
    @GetMapping("/{symbol}")
    public ResponseEntity<FundamentalsView> getFundamentals(
            @Parameter(description = "NSE symbol", example = "RELIANCE")
            @PathVariable String symbol) {
        return fundamentalsService.getFundamentals(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
