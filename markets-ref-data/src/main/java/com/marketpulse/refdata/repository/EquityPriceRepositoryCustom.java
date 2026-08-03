package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityPrice;
import java.util.List;

public interface EquityPriceRepositoryCustom {

    /** Idempotent bulk write: inserts new (trade_date, symbol) rows, overwrites existing ones. */
    void upsertAll(List<EquityPrice> rows);
}
