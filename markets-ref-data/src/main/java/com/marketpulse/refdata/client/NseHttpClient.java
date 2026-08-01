package com.marketpulse.refdata.client;

import com.marketpulse.refdata.config.NseProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HTTP client tailored for NSE requests. NSE blocks direct archive downloads unless a
 * session has already been established against the base domain (to obtain cookies) and
 * browser-like headers are supplied - see NseProperties for the header set.
 */
@Component
public class NseHttpClient {

    private static final Logger log = LoggerFactory.getLogger(NseHttpClient.class);

    private final HttpClient httpClient;
    private final NseProperties properties;

    public NseHttpClient(HttpClient httpClient, NseProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
        initializeSession();
    }

    /** Visits the NSE home page to grab the cookies required before archive downloads succeed. */
    private void initializeSession() {
        try {
            HttpRequest request = requestBuilder(properties.getBaseUrl()).build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            log.error("Failed to initialize NSE session: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to initialize NSE session: {}", e.getMessage());
        }
    }

    /** Downloads a file from the given URL and returns its raw bytes. */
    public byte[] downloadFile(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(url).build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new NseHttpException(response.statusCode(), "HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        properties.getHeaders().forEach(builder::header);
        return builder;
    }
}
