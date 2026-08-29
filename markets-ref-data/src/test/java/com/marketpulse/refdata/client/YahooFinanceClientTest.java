package com.marketpulse.refdata.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.refdata.config.YahooFinanceProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooFinanceClientTest {

    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final String CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    private static final String QUOTE_SUMMARY_URL = "https://query1.finance.yahoo.com/v10/finance/quoteSummary";
    private static final String CRUMB = "abc123";

    private static final String SUCCESS_BODY =
            """
            {"quoteSummary":{"result":[{"price":{"longName":"Reliance Industries Limited"},
            "summaryProfile":{"sector":"Energy","industry":"Oil & Gas Refining & Marketing",
            "longBusinessSummary":"Reliance Industries Limited engages in hydrocarbon exploration."},
            "summaryDetail":{"trailingPE":{"raw":23.899258},"forwardPE":{"raw":18.442806},
            "marketCap":{"raw":17849330434048},"fiftyTwoWeekLow":{"raw":1249.8},
            "fiftyTwoWeekHigh":{"raw":1611.8},"dividendYield":{"raw":0.0046}}}],"error":null}}
            """;

    private static final String NOT_FOUND_BODY =
            """
            {"quoteSummary":{"result":null,"error":{"code":"Not Found","description":"Quote not found for symbol: FAKE.NS"}}}
            """;

    private HttpClient httpClient;
    private HttpResponse<Object> cookieResponse;
    private HttpResponse<Object> crumbResponse;
    private HttpResponse<Object> quoteResponse;
    private YahooFinanceProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = mock(HttpClient.class);
        cookieResponse = mock(HttpResponse.class);
        crumbResponse = mock(HttpResponse.class);
        quoteResponse = mock(HttpResponse.class);

        properties = new YahooFinanceProperties();
        properties.setCookieWarmupUrl(COOKIE_URL);
        properties.setCrumbUrl(CRUMB_URL);
        properties.setQuoteSummaryUrl(QUOTE_SUMMARY_URL);

        when(crumbResponse.body()).thenReturn(CRUMB);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchesFundamentalsSuccessfully() throws Exception {
        when(quoteResponse.statusCode()).thenReturn(200);
        when(quoteResponse.body()).thenReturn(SUCCESS_BODY.getBytes(StandardCharsets.UTF_8));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> respondByUri(invocation.getArgument(0)));

        YahooFinanceClient client = new YahooFinanceClient(httpClient, properties, new ObjectMapper());
        JsonNode result = client.getFundamentals("RELIANCE");

        assertThat(result.path("price").path("longName").asText()).isEqualTo("Reliance Industries Limited");
        assertThat(result.path("summaryDetail").path("trailingPE").path("raw").asDouble()).isEqualTo(23.899258);
        assertThat(result.path("summaryProfile").path("sector").asText()).isEqualTo("Energy");
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsYahooFinanceExceptionOn404() throws Exception {
        when(quoteResponse.statusCode()).thenReturn(404);
        when(quoteResponse.body()).thenReturn(NOT_FOUND_BODY.getBytes(StandardCharsets.UTF_8));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> respondByUri(invocation.getArgument(0)));

        YahooFinanceClient client = new YahooFinanceClient(httpClient, properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getFundamentals("FAKE"))
                .isInstanceOf(YahooFinanceException.class)
                .satisfies(e -> assertThat(((YahooFinanceException) e).getStatusCode()).isEqualTo(404));
    }

    private HttpResponse<Object> respondByUri(HttpRequest request) {
        String uri = request.uri().toString();
        if (uri.equals(COOKIE_URL)) {
            return cookieResponse;
        }
        if (uri.startsWith(CRUMB_URL)) {
            return crumbResponse;
        }
        return quoteResponse;
    }
}
