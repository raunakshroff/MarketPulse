package com.marketpulse.refdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Master/dimension table of known equity tickers, kept separate from the {@code equity_price} hypertable. */
@Entity
@Table(name = "equity_symbol")
public class EquitySymbol {

    @Id
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "first_seen_date", nullable = false)
    private LocalDate firstSeenDate;

    @Column(name = "last_seen_date", nullable = false)
    private LocalDate lastSeenDate;

    protected EquitySymbol() {
        // JPA
    }

    public EquitySymbol(String symbol, LocalDate firstSeenDate, LocalDate lastSeenDate) {
        this.symbol = symbol;
        this.firstSeenDate = firstSeenDate;
        this.lastSeenDate = lastSeenDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getFirstSeenDate() {
        return firstSeenDate;
    }

    public LocalDate getLastSeenDate() {
        return lastSeenDate;
    }
}
