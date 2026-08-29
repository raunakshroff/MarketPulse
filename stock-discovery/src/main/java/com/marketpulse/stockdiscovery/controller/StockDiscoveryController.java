package com.marketpulse.stockdiscovery.controller;

import com.marketpulse.stockdiscovery.model.EquityRecord;
import com.marketpulse.stockdiscovery.model.EquitySearchResult;
import com.marketpulse.stockdiscovery.model.FundamentalsView;
import com.marketpulse.stockdiscovery.service.StockDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
@Tag(name = "Stocks", description = "Read-only search and lookup over stored NSE equity data")
public class StockDiscoveryController {

    private final StockDiscoveryService stockDiscoveryService;

    public StockDiscoveryController(StockDiscoveryService stockDiscoveryService) {
        this.stockDiscoveryService = stockDiscoveryService;
    }

    /** Symbol/company-name search, e.g. ?q=RELI. */
    @Operation(
            summary = "Search symbols by ticker or company name",
            description = """
                    Case-insensitive partial match against the ticker symbol and, where fundamentals \
                    have been fetched, the company name. Backs the UI's search box. Returns an empty \
                    list (200, not 404) when nothing matches.""")
    @ApiResponse(responseCode = "200", description = "Matching symbols, possibly empty")
    @GetMapping("/search")
    public ResponseEntity<List<EquitySearchResult>> search(
            @Parameter(description = "Partial symbol or company name", example = "RELI")
            @RequestParam String q,
            @Parameter(description = "Maximum results to return")
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(stockDiscoveryService.search(q, limit));
    }

    /** Full price history for a symbol, ordered by date ascending, for charting. */
    @Operation(
            summary = "Get full price history for a symbol",
            description = "Complete stored OHLCV history, oldest first - the series the UI's chart is drawn from.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Price history, oldest first"),
            @ApiResponse(responseCode = "404", description = "No price data stored for this symbol")
    })
    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<EquityRecord>> getHistory(
            @Parameter(description = "NSE symbol", example = "RELIANCE")
            @PathVariable String symbol) {
        List<EquityRecord> history = stockDiscoveryService.getHistory(symbol);
        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    /** Previously fetched fundamentals for a symbol, 404 if none have been fetched yet. */
    @Operation(
            summary = "Get fundamentals for a symbol",
            description = """
                    Fundamentals are populated on demand by markets-ref-data, so a 404 here means \
                    "not fetched yet" rather than "no such symbol". The UI degrades gracefully on \
                    this case and still renders the price chart.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fundamentals found"),
            @ApiResponse(responseCode = "404", description = "Not fetched for this symbol yet")
    })
    @GetMapping("/{symbol}/fundamentals")
    public ResponseEntity<FundamentalsView> getFundamentals(
            @Parameter(description = "NSE symbol", example = "RELIANCE")
            @PathVariable String symbol) {
        return stockDiscoveryService.getFundamentals(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
