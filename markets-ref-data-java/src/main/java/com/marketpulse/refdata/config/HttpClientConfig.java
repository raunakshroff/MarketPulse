package com.marketpulse.refdata.config;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    /**
     * A single shared HttpClient with an in-memory cookie jar, mirroring the Python
     * NSEHttpClient's use of requests.Session to persist cookies across the session
     * warm-up call and subsequent archive downloads.
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
