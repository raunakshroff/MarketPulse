package com.marketpulse.refdata.repository;

import java.time.LocalDate;
import java.util.List;

public interface EquitySymbolRepositoryCustom {

    /**
     * Registers each symbol as seen on {@code seenDate}: inserts new symbols, and for existing ones
     * widens the [first_seen_date, last_seen_date] window to include {@code seenDate} - moving
     * first_seen_date earlier or last_seen_date later as needed, never the other way. This keeps the
     * window correct regardless of the order dates are loaded in, which matters for backfills that
     * load history out of chronological order.
     */
    void upsertAll(List<String> symbols, LocalDate seenDate);
}
