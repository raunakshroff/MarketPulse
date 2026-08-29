package com.marketpulse.stockdiscovery.service;

import com.marketpulse.stockdiscovery.entity.EquityFundamentals;
import com.marketpulse.stockdiscovery.entity.EquityPrice;
import com.marketpulse.stockdiscovery.model.EquityRecord;
import com.marketpulse.stockdiscovery.model.EquitySearchResult;
import com.marketpulse.stockdiscovery.model.FundamentalsView;
import com.marketpulse.stockdiscovery.repository.EquityFundamentalsRepository;
import com.marketpulse.stockdiscovery.repository.EquityPriceRepository;
import com.marketpulse.stockdiscovery.repository.EquitySearchRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-model APIs for the UI: symbol search, price history for charting, and fundamentals. */
@Service
public class StockDiscoveryService {

    private static final DateTimeFormatter WIRE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final EquitySearchRepository equitySearchRepository;
    private final EquityPriceRepository equityPriceRepository;
    private final EquityFundamentalsRepository equityFundamentalsRepository;

    public StockDiscoveryService(
            EquitySearchRepository equitySearchRepository,
            EquityPriceRepository equityPriceRepository,
            EquityFundamentalsRepository equityFundamentalsRepository) {
        this.equitySearchRepository = equitySearchRepository;
        this.equityPriceRepository = equityPriceRepository;
        this.equityFundamentalsRepository = equityFundamentalsRepository;
    }

    public List<EquitySearchResult> search(String query, int limit) {
        return equitySearchRepository.search(query, limit);
    }

    public List<EquityRecord> getHistory(String symbol) {
        return equityPriceRepository.findBySymbolOrderByTradeDateAsc(symbol).stream()
                .map(StockDiscoveryService::toEquityRecord)
                .toList();
    }

    public Optional<FundamentalsView> getFundamentals(String symbol) {
        return equityFundamentalsRepository.findById(symbol).map(StockDiscoveryService::toView);
    }

    private static EquityRecord toEquityRecord(EquityPrice entity) {
        return new EquityRecord(
                entity.getSymbol(),
                entity.getSeries(),
                entity.getTradeDate().format(WIRE_DATE_FORMAT),
                entity.getPrevClose(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getLastPrice(),
                entity.getClosePrice(),
                entity.getAvgPrice(),
                entity.getTtlTradedQty(),
                entity.getTurnoverLacs(),
                entity.getNoOfTrades(),
                entity.getDelivQty(),
                entity.getDelivPer());
    }

    private static FundamentalsView toView(EquityFundamentals entity) {
        return new FundamentalsView(
                entity.getSymbol(),
                entity.getCompanyName(),
                entity.getSector(),
                entity.getIndustry(),
                entity.getDescription(),
                entity.getMarketCap(),
                entity.getTrailingPe(),
                entity.getForwardPe(),
                entity.getFiftyTwoWeekLow(),
                entity.getFiftyTwoWeekHigh(),
                entity.getDividendYield(),
                entity.getUpdatedAt());
    }
}
