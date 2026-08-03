package com.marketpulse.refdata.repository;

import java.time.LocalDate;
import java.util.List;

public interface EquitySymbolRepositoryCustom {

    /** Registers each symbol as seen on {@code seenDate}: inserts new symbols, advances last_seen_date on existing ones. */
    void upsertAll(List<String> symbols, LocalDate seenDate);
}
