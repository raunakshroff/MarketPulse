package com.marketpulse.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * markets-admin is mounted under /admin/**, with that prefix stripped before forwarding so it
 * keeps thinking it's served from its own root exactly as it is on its direct port; everything
 * else falls through to markets-ui. This is what lets a browser stay on one origin (this
 * gateway) when navigating between the dashboard and the admin console, instead of the address
 * bar jumping to markets-admin's own port.
 *
 * <p>Both routes are combined into a single bean via {@code and()} so the more specific
 * /admin/** route is always tried before the /** catch-all, rather than depending on Spring's
 * (unspecified) ordering of multiple RouterFunction beans.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(GatewayProperties properties) {
        // markets-admin's own page references its assets/API with relative (not root-absolute)
        // paths specifically so it resolves correctly under this /admin/ mount - which only works
        // if the browser is actually sitting on the trailing slash. Redirect the bare path so a
        // typed-or-bookmarked /admin doesn't silently half-load.
        RouterFunction<ServerResponse> adminTrailingSlashRedirect = RouterFunctions.route(
                RequestPredicates.GET("/admin"),
                req -> ServerResponse.status(HttpStatus.FOUND).header("Location", "/admin/").build());

        RouterFunction<ServerResponse> adminRoute = route("markets-admin")
                .route(path("/admin/**"), http())
                .before(stripPrefix(1))
                .before(uri(properties.adminUrl()))
                .build();

        RouterFunction<ServerResponse> uiRoute = route("markets-ui")
                .route(path("/**"), http())
                .before(uri(properties.uiUrl()))
                .build();

        return adminTrailingSlashRedirect.and(adminRoute).and(uiRoute);
    }
}
