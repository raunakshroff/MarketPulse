package com.marketpulse.refdata.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquitySymbolRepositoryImpl implements EquitySymbolRepositoryCustom {

    private static final String UPSERT_SQL = """
            INSERT INTO equity_symbol (symbol, first_seen_date, last_seen_date)
            VALUES (?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
                last_seen_date = EXCLUDED.last_seen_date
            """;

    private final JdbcTemplate jdbcTemplate;

    public EquitySymbolRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertAll(List<String> symbols, LocalDate seenDate) {
        jdbcTemplate.batchUpdate(UPSERT_SQL, symbols, 500, (ps, symbol) -> {
            ps.setString(1, symbol);
            ps.setObject(2, seenDate);
            ps.setObject(3, seenDate);
        });
    }
}
