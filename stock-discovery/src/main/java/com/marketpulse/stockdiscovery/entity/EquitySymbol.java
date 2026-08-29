package com.marketpulse.stockdiscovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Read-only mapping of the {@code equity_symbol} dimension table. The schema is owned and
 * migrated by markets-ref-data; this service only ever reads it.
 */
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
