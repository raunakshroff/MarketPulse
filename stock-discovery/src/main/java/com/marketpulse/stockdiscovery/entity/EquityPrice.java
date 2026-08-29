package com.marketpulse.stockdiscovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only mapping of one equity's Bhavcopy row for one trading day, from the
 * {@code equity_price} hypertable owned and migrated by markets-ref-data.
 */
@Entity
@Table(name = "equity_price")
@IdClass(EquityPriceId.class)
public class EquityPrice {

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Id
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "series", nullable = false, length = 10)
    private String series;

    @Column(name = "prev_close", precision = 18, scale = 4)
    private BigDecimal prevClose;

    @Column(name = "open_price", precision = 18, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "last_price", precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "close_price", precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "avg_price", precision = 18, scale = 4)
    private BigDecimal avgPrice;

    @Column(name = "ttl_trd_qty", nullable = false)
    private long ttlTradedQty;

    @Column(name = "turnover_lacs", precision = 18, scale = 4)
    private BigDecimal turnoverLacs;

    @Column(name = "no_of_trades", nullable = false)
    private long noOfTrades;

    @Column(name = "deliv_qty", nullable = false)
    private long delivQty;

    @Column(name = "deliv_per", precision = 9, scale = 4)
    private BigDecimal delivPer;

    protected EquityPrice() {
        // JPA
    }

    public EquityPrice(
            LocalDate tradeDate,
            String symbol,
            String series,
            BigDecimal prevClose,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal lastPrice,
            BigDecimal closePrice,
            BigDecimal avgPrice,
            long ttlTradedQty,
            BigDecimal turnoverLacs,
            long noOfTrades,
            long delivQty,
            BigDecimal delivPer) {
        this.tradeDate = tradeDate;
        this.symbol = symbol;
        this.series = series;
        this.prevClose = prevClose;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.lastPrice = lastPrice;
        this.closePrice = closePrice;
        this.avgPrice = avgPrice;
        this.ttlTradedQty = ttlTradedQty;
        this.turnoverLacs = turnoverLacs;
        this.noOfTrades = noOfTrades;
        this.delivQty = delivQty;
        this.delivPer = delivPer;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSeries() {
        return series;
    }

    public BigDecimal getPrevClose() {
        return prevClose;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public long getTtlTradedQty() {
        return ttlTradedQty;
    }

    public BigDecimal getTurnoverLacs() {
        return turnoverLacs;
    }

    public long getNoOfTrades() {
        return noOfTrades;
    }

    public long getDelivQty() {
        return delivQty;
    }

    public BigDecimal getDelivPer() {
        return delivPer;
    }
}
