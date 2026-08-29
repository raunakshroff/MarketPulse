package com.marketpulse.stockdiscovery.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite key for {@link EquityPrice}: one row per symbol per trading day. */
public class EquityPriceId implements Serializable {

    private LocalDate tradeDate;
    private String symbol;

    public EquityPriceId() {
    }

    public EquityPriceId(LocalDate tradeDate, String symbol) {
        this.tradeDate = tradeDate;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EquityPriceId that)) {
            return false;
        }
        return Objects.equals(tradeDate, that.tradeDate) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeDate, symbol);
    }
}
