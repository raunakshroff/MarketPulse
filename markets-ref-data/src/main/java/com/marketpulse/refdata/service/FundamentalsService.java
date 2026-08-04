package com.marketpulse.refdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketpulse.refdata.client.YahooFinanceClient;
import com.marketpulse.refdata.client.YahooFinanceException;
import com.marketpulse.refdata.entity.EquityFundamentals;
import com.marketpulse.refdata.model.FundamentalsResult;
import com.marketpulse.refdata.model.FundamentalsView;
import com.marketpulse.refdata.repository.EquityFundamentalsRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fetches and persists company fundamentals for individual symbols, on demand. */
@Service
public class FundamentalsService {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsService.class);

    private final YahooFinanceClient yahooFinanceClient;
    private final EquityFundamentalsRepository equityFundamentalsRepository;

    public FundamentalsService(
            YahooFinanceClient yahooFinanceClient, EquityFundamentalsRepository equityFundamentalsRepository) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.equityFundamentalsRepository = equityFundamentalsRepository;
    }

    /** Refreshes each symbol independently - one bad symbol doesn't abort the rest of the batch. */
    public List<FundamentalsResult> refresh(List<String> symbols) {
        return symbols.stream().map(this::refreshOne).toList();
    }

    @Transactional
    protected FundamentalsResult refreshOne(String symbol) {
        log.info("Fetching fundamentals for: {}", symbol);
        try {
            JsonNode result = yahooFinanceClient.getFundamentals(symbol);
            equityFundamentalsRepository.upsert(toEntity(symbol, result));
            log.info("Success! Saved fundamentals for {}", symbol);
            return FundamentalsResult.success(symbol);
        } catch (YahooFinanceException e) {
            if (e.getStatusCode() == 404) {
                log.warn("No fundamentals found for {}: {}", symbol, e.getMessage());
                return FundamentalsResult.notFound(symbol);
            }
            log.error("HTTP error fetching fundamentals for {}: {}", symbol, e.getMessage());
            return FundamentalsResult.failure(symbol, e.getMessage());
        } catch (IOException e) {
            log.error("Unexpected error fetching fundamentals for {}: {}", symbol, e.getMessage());
            return FundamentalsResult.failure(symbol, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Unexpected error fetching fundamentals for {}: {}", symbol, e.getMessage());
            return FundamentalsResult.failure(symbol, e.getMessage());
        }
    }

    public Optional<FundamentalsView> getFundamentals(String symbol) {
        return equityFundamentalsRepository.findById(symbol).map(FundamentalsService::toView);
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

    private static EquityFundamentals toEntity(String symbol, JsonNode result) {
        JsonNode summaryDetail = result.path("summaryDetail");
        JsonNode summaryProfile = result.path("summaryProfile");
        JsonNode price = result.path("price");

        return new EquityFundamentals(
                symbol,
                textOrNull(price, "longName"),
                textOrNull(summaryProfile, "sector"),
                textOrNull(summaryProfile, "industry"),
                textOrNull(summaryProfile, "longBusinessSummary"),
                rawDecimalOrNull(summaryDetail, "marketCap"),
                rawDecimalOrNull(summaryDetail, "trailingPE"),
                rawDecimalOrNull(summaryDetail, "forwardPE"),
                rawDecimalOrNull(summaryDetail, "fiftyTwoWeekLow"),
                rawDecimalOrNull(summaryDetail, "fiftyTwoWeekHigh"),
                rawDecimalOrNull(summaryDetail, "dividendYield"),
                OffsetDateTime.now());
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /** Yahoo wraps numeric fields as {"raw": <value>, "fmt": "<display string>"}. */
    private static BigDecimal rawDecimalOrNull(JsonNode parent, String field) {
        JsonNode raw = parent.path(field).path("raw");
        return raw.isMissingNode() || raw.isNull() ? null : BigDecimal.valueOf(raw.asDouble());
    }
}
