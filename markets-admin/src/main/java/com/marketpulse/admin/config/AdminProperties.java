package com.marketpulse.admin.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The service registry, bound from {@code admin.*} in application.yml.
 *
 * <p>This is the single place the console's contents are configured — adding a future module
 * (Watchlist, Portfolio, Alerts, …) to the console is a YAML entry here, not a code change.
 *
 * <p><b>Why two URLs per service.</b> The console resolves each service twice, because the two
 * consumers sit on different sides of the network. {@code internalUrl} is used by this service to
 * probe health server-side, so under Docker it is the compose DNS name
 * ({@code http://markets-ref-data:8081}). {@code externalUrl} is what gets baked into the links a
 * browser follows, so it must be an address the operator's machine can reach
 * ({@code http://localhost:8081}). Collapsing these into one value breaks whichever consumer is on
 * the far side.
 *
 * @param refreshOnLoad whether the console probes health as soon as the page opens
 * @param services      the services to show, in display order
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(boolean refreshOnLoad, List<ManagedService> services) {

    public AdminProperties {
        services = services == null ? List.of() : List.copyOf(services);
    }

    /**
     * @param id           stable slug, used as the API path segment and DOM id
     * @param name         display name, normally the module directory name
     * @param role         one-line positioning, e.g. "Write / ingestion"
     * @param description  what the service is for
     * @param internalUrl  base URL this service probes from the server side
     * @param externalUrl  base URL the browser links to
     * @param healthPath   path probed for health; Actuator services use /actuator/health, a plain
     *                     static site uses / (any 2xx counts as UP)
     * @param links        operational links, resolved against externalUrl for display
     */
    public record ManagedService(
            String id,
            String name,
            String role,
            String description,
            String internalUrl,
            String externalUrl,
            String healthPath,
            List<Link> links) {

        public ManagedService {
            healthPath = (healthPath == null || healthPath.isBlank()) ? "/actuator/health" : healthPath;
            links = links == null ? List.of() : List.copyOf(links);
        }
    }

    /**
     * @param label       link text
     * @param path        path appended to the service's externalUrl
     * @param description why an operator would click it
     */
    public record Link(String label, String path, String description) {}
}
