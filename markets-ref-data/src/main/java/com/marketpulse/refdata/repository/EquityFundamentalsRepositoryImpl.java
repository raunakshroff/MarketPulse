package com.marketpulse.refdata.repository;

import com.marketpulse.refdata.entity.EquityFundamentals;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquityFundamentalsRepositoryImpl implements EquityFundamentalsRepositoryCustom {

    private static final String UPSERT_SQL = """
            INSERT INTO equity_fundamentals (
                symbol, company_name, sector, industry, description, market_cap,
                trailing_pe, forward_pe, fifty_two_week_low, fifty_two_week_high,
                dividend_yield, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (symbol) DO UPDATE SET
                company_name = EXCLUDED.company_name,
                sector = EXCLUDED.sector,
                industry = EXCLUDED.industry,
                description = EXCLUDED.description,
                market_cap = EXCLUDED.market_cap,
                trailing_pe = EXCLUDED.trailing_pe,
                forward_pe = EXCLUDED.forward_pe,
                fifty_two_week_low = EXCLUDED.fifty_two_week_low,
                fifty_two_week_high = EXCLUDED.fifty_two_week_high,
                dividend_yield = EXCLUDED.dividend_yield,
                updated_at = EXCLUDED.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public EquityFundamentalsRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(EquityFundamentals row) {
        jdbcTemplate.update(
                UPSERT_SQL,
                row.getSymbol(),
                row.getCompanyName(),
                row.getSector(),
                row.getIndustry(),
                row.getDescription(),
                row.getMarketCap(),
                row.getTrailingPe(),
                row.getForwardPe(),
                row.getFiftyTwoWeekLow(),
                row.getFiftyTwoWeekHigh(),
                row.getDividendYield(),
                row.getUpdatedAt());
    }
}
