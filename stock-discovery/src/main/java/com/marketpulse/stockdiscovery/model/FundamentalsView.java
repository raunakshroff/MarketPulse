package com.marketpulse.stockdiscovery.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FundamentalsView(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String description,
        BigDecimal marketCap,
        BigDecimal trailingPe,
        BigDecimal forwardPe,
        BigDecimal fiftyTwoWeekLow,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal dividendYield,
        OffsetDateTime updatedAt) {
}
