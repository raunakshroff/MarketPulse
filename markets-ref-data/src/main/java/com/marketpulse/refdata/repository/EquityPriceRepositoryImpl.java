package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityPrice;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquityPriceRepositoryImpl implements EquityPriceRepositoryCustom {

    private static final String UPSERT_SQL = """
            INSERT INTO equity_price (
                trade_date, symbol, series, prev_close, open_price, high_price, low_price,
                last_price, close_price, avg_price, ttl_trd_qty, turnover_lacs,
                no_of_trades, deliv_qty, deliv_per)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (trade_date, symbol) DO UPDATE SET
                series = EXCLUDED.series,
                prev_close = EXCLUDED.prev_close,
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                last_price = EXCLUDED.last_price,
                close_price = EXCLUDED.close_price,
                avg_price = EXCLUDED.avg_price,
                ttl_trd_qty = EXCLUDED.ttl_trd_qty,
                turnover_lacs = EXCLUDED.turnover_lacs,
                no_of_trades = EXCLUDED.no_of_trades,
                deliv_qty = EXCLUDED.deliv_qty,
                deliv_per = EXCLUDED.deliv_per
            """;

    private final JdbcTemplate jdbcTemplate;

    public EquityPriceRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertAll(List<EquityPrice> rows) {
        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, row.getTradeDate());
            ps.setString(2, row.getSymbol());
            ps.setString(3, row.getSeries());
            ps.setBigDecimal(4, row.getPrevClose());
            ps.setBigDecimal(5, row.getOpenPrice());
            ps.setBigDecimal(6, row.getHighPrice());
            ps.setBigDecimal(7, row.getLowPrice());
            ps.setBigDecimal(8, row.getLastPrice());
            ps.setBigDecimal(9, row.getClosePrice());
            ps.setBigDecimal(10, row.getAvgPrice());
            ps.setLong(11, row.getTtlTradedQty());
            ps.setBigDecimal(12, row.getTurnoverLacs());
            ps.setLong(13, row.getNoOfTrades());
            ps.setLong(14, row.getDelivQty());
            ps.setBigDecimal(15, row.getDelivPer());
        });
    }
}
