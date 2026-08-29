package com.marketpulse.refdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI is served at /swagger-ui.html, the raw OpenAPI 3 document at /v3/api-docs.
 *
 * <p>springdoc discovers the endpoints themselves from the Spring MVC annotations; this bean only
 * supplies the document-level metadata that can't be inferred.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI refDataOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MarketPulse - markets-ref-data")
                .version("v1")
                .description("""
                        The write/ingestion side of MarketPulse. Fetches daily NSE Bhavcopy EOD equity \
                        prices (on a 19:00 IST weekday schedule, and on demand here) plus company \
                        fundamentals from Yahoo Finance, persisting both to TimescaleDB. This service \
                        owns the database schema via Flyway.

                        Read-side queries for the UI live in the separate stock-discovery service."""));
    }
}
