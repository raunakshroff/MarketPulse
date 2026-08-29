package com.marketpulse.stockdiscovery.repository;

import com.marketpulse.stockdiscovery.model.EquitySearchResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Symbol/company-name search. Matches on {@code equity_symbol.symbol} (always populated) or
 * {@code equity_fundamentals.company_name} (only populated for symbols someone has refreshed
 * fundamentals for) via a LEFT JOIN, so a symbol with no fundamentals yet still matches on its
 * ticker.
 */
@Repository
public class EquitySearchRepository {

    private static final String SEARCH_SQL = """
            SELECT s.symbol, f.company_name, f.sector
            FROM equity_symbol s
            LEFT JOIN equity_fundamentals f ON f.symbol = s.symbol
            WHERE s.symbol ILIKE ? OR f.company_name ILIKE ?
            ORDER BY s.symbol
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public EquitySearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EquitySearchResult> search(String query, int limit) {
        String pattern = "%" + query + "%";
        return jdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> new EquitySearchResult(
                        rs.getString("symbol"), rs.getString("company_name"), rs.getString("sector")),
                pattern, pattern, limit);
    }
}
