package com.marketpulse.stockdiscovery.model;

import java.math.BigDecimal;

public record EquityRecord(
        String symbol,
        String series,
        String date,
        BigDecimal prevClose,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal lastPrice,
        BigDecimal closePrice,
        BigDecimal avgPrice,
        long ttlTradedQty,
        BigDecimal turnoverLacs,
        long noOfTrades,
        long delivQty,
        BigDecimal delivPer) {
}
