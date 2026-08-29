package com.marketpulse.stockdiscovery.config;

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
    public OpenAPI stockDiscoveryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MarketPulse - stock-discovery")
                .version("v1")
                .description("""
                        The read/query side of MarketPulse, backing the markets-ui frontend. Serves \
                        symbol search, price history and fundamentals from the same TimescaleDB \
                        database that markets-ref-data ingests into.

                        This service is strictly read-only: it never migrates or writes to the schema, \
                        which markets-ref-data owns."""));
    }
}
