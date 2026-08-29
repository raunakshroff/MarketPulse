package com.marketpulse.stockdiscovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only mapping of company fundamentals from the {@code equity_fundamentals} table owned
 * and migrated by markets-ref-data (populated there via the on-demand Yahoo Finance refresh).
 */
@Entity
@Table(name = "equity_fundamentals")
public class EquityFundamentals {

    @Id
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "sector", length = 255)
    private String sector;

    @Column(name = "industry", length = 255)
    private String industry;

    @Column(name = "description")
    private String description;

    @Column(name = "market_cap", precision = 24, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "trailing_pe", precision = 18, scale = 4)
    private BigDecimal trailingPe;

    @Column(name = "forward_pe", precision = 18, scale = 4)
    private BigDecimal forwardPe;

    @Column(name = "fifty_two_week_low", precision = 18, scale = 4)
    private BigDecimal fiftyTwoWeekLow;

    @Column(name = "fifty_two_week_high", precision = 18, scale = 4)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(name = "dividend_yield", precision = 9, scale = 4)
    private BigDecimal dividendYield;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EquityFundamentals() {
        // JPA
    }

    public EquityFundamentals(
            String symbol,
            String companyName,
            String sector,
            String industry,
            String description,
            BigDecimal marketCap,
            BigDecimal trailingPe,
            BigDecimal forwardPe,
            BigDecimal fiftyTwoWeekLow,
            BigDecimal fiftyTwoWeekHigh,
            BigDecimal dividendYield,
            OffsetDateTime updatedAt) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.sector = sector;
        this.industry = industry;
        this.description = description;
        this.marketCap = marketCap;
        this.trailingPe = trailingPe;
        this.forwardPe = forwardPe;
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
        this.dividendYield = dividendYield;
        this.updatedAt = updatedAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getSector() {
        return sector;
    }

    public String getIndustry() {
        return industry;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public BigDecimal getTrailingPe() {
        return trailingPe;
    }

    public BigDecimal getForwardPe() {
        return forwardPe;
    }

    public BigDecimal getFiftyTwoWeekLow() {
        return fiftyTwoWeekLow;
    }

    public BigDecimal getFiftyTwoWeekHigh() {
        return fiftyTwoWeekHigh;
    }

    public BigDecimal getDividendYield() {
        return dividendYield;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
