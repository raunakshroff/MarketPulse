package com.marketpulse.refdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.refdata.config.YahooFinanceProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches company fundamentals from Yahoo Finance's unofficial quoteSummary endpoint, using NSE
 * symbols with a ".NS" suffix (e.g. "RELIANCE.NS"). A plain request 401s with "Invalid Crumb" -
 * Yahoo requires warming up a cookie and exchanging it for a crumb before the real request.
 */
@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);
    private static final String MODULES = "summaryProfile,summaryDetail,price";

    private final HttpClient httpClient;
    private final YahooFinanceProperties properties;
    private final ObjectMapper objectMapper;
    private final String crumb;

    public YahooFinanceClient(HttpClient httpClient, YahooFinanceProperties properties, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.crumb = fetchCrumb();
    }

    /** Returns the {@code quoteSummary.result[0]} node for the given NSE symbol. */
    public JsonNode getFundamentals(String symbol) throws IOException, InterruptedException {
        String encodedSymbol = URLEncoder.encode(symbol + ".NS", StandardCharsets.UTF_8);
        String encodedCrumb = URLEncoder.encode(crumb, StandardCharsets.UTF_8);
        String url = "%s/%s?modules=%s&crumb=%s".formatted(
                properties.getQuoteSummaryUrl(), encodedSymbol, MODULES, encodedCrumb);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        JsonNode body = objectMapper.readTree(response.body());
        if (response.statusCode() != 200) {
            String description = body.path("quoteSummary").path("error").path("description").asText(
                    "HTTP " + response.statusCode() + " for symbol " + symbol);
            throw new YahooFinanceException(response.statusCode(), description);
        }

        JsonNode result = body.path("quoteSummary").path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new YahooFinanceException(404, "No quoteSummary result for symbol " + symbol);
        }
        return result.get(0);
    }

    /** Warms up a cookie, then exchanges it for the crumb Yahoo requires on every subsequent request. */
    private String fetchCrumb() {
        try {
            HttpRequest warmup = HttpRequest.newBuilder(URI.create(properties.getCookieWarmupUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            httpClient.send(warmup, HttpResponse.BodyHandlers.discarding());

            HttpRequest crumbRequest = HttpRequest.newBuilder(URI.create(properties.getCrumbUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> crumbResponse = httpClient.send(crumbRequest, HttpResponse.BodyHandlers.ofString());
            if (crumbResponse.statusCode() != 200) {
                log.error("Failed to fetch Yahoo Finance crumb: HTTP {}", crumbResponse.statusCode());
                return "";
            }
            return crumbResponse.body();
        } catch (IOException e) {
            log.error("Failed to fetch Yahoo Finance crumb: {}", e.getMessage());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to fetch Yahoo Finance crumb: {}", e.getMessage());
            return "";
        }
    }
}
