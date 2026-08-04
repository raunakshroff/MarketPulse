package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityFundamentals;

public interface EquityFundamentalsRepositoryCustom {

    /** Idempotent write: inserts a new symbol's fundamentals, overwrites an existing snapshot. */
    void upsert(EquityFundamentals row);
}
