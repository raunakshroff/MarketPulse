-- first_seen_date was never revised and last_seen_date was overwritten unconditionally, so any
-- symbol loaded before the upsert became monotonic carries wrong dates. Recompute both from the
-- price rows, which are the source of truth. Safe to run on an empty table.
UPDATE equity_symbol s
SET first_seen_date = agg.min_trade_date,
    last_seen_date = agg.max_trade_date
FROM (
    SELECT symbol, MIN(trade_date) AS min_trade_date, MAX(trade_date) AS max_trade_date
    FROM equity_price
    GROUP BY symbol
) agg
WHERE s.symbol = agg.symbol
  AND (s.first_seen_date <> agg.min_trade_date OR s.last_seen_date <> agg.max_trade_date);
