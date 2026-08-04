package com.marketpulse.refdata.controller;

import com.marketpulse.refdata.model.FundamentalsResult;
import com.marketpulse.refdata.model.FundamentalsView;
import com.marketpulse.refdata.service.FundamentalsService;
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
public class FundamentalsController {

    private final FundamentalsService fundamentalsService;

    public FundamentalsController(FundamentalsService fundamentalsService) {
        this.fundamentalsService = fundamentalsService;
    }

    /** Fetches and stores fundamentals for one or more symbols (comma-separated), e.g. ?symbols=RELIANCE,TCS. */
    @PostMapping("/refresh")
    public ResponseEntity<List<FundamentalsResult>> refresh(@RequestParam List<String> symbols) {
        return ResponseEntity.ok(fundamentalsService.refresh(symbols));
    }

    /** Returns the previously fetched fundamentals for a symbol. */
    @GetMapping("/{symbol}")
    public ResponseEntity<FundamentalsView> getFundamentals(@PathVariable String symbol) {
        return fundamentalsService.getFundamentals(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
