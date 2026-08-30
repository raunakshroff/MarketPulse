package com.marketpulse.admin.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The shared {@link HttpClient} used to probe the other services.
 *
 * <p>Injected rather than constructed inside the probe so tests can mock it — the same reason
 * {@code markets-ref-data} injects its own client. Redirects are not followed: a health endpoint
 * answering with a redirect is a misconfiguration worth surfacing, not something to chase.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
