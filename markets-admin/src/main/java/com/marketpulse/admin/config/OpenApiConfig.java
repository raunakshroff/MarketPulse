package com.marketpulse.admin.config;

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
    public OpenAPI adminOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MarketPulse - markets-admin")
                .version("v1")
                .description("""
                        The operations console for MarketPulse. Serves a single admin page covering \
                        every service in the system, and the API behind it: the configured service \
                        registry plus a live, server-side health probe of each entry.

                        This service holds no state and has no database, so it can start and report \
                        on the others even when the database is down."""));
    }
}
