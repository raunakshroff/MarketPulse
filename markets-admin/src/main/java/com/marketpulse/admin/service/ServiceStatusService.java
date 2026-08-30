package com.marketpulse.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketpulse.admin.config.AdminProperties;
import com.marketpulse.admin.model.ServiceStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads the configured service registry and enriches each entry with a live health probe.
 *
 * <p>Probes run <b>in parallel</b>: with a per-probe timeout, checking services one at a time would
 * make the console's load time the <i>sum</i> of every unreachable service's timeout, which is
 * exactly the situation the console exists to report on.
 */
@Service
public class ServiceStatusService {

    private static final Logger log = LoggerFactory.getLogger(ServiceStatusService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final AdminProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServiceStatusService(AdminProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** Every configured service, in configuration order, each with a freshly probed health. */
    public List<ServiceStatus> getAll() {
        List<CompletableFuture<ServiceStatus>> pending = properties.services().stream()
                .map(s -> CompletableFuture.supplyAsync(() -> probe(s)))
                .toList();
        return pending.stream().map(CompletableFuture::join).toList();
    }

    /** One service by its configured id, or empty when no such id is configured. */
    public java.util.Optional<ServiceStatus> getById(String id) {
        return properties.services().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .map(this::probe);
    }

    private ServiceStatus probe(AdminProperties.ManagedService service) {
        long startedAt = System.nanoTime();
        ServiceStatus.Health health;
        String detail;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(service.internalUrl()) + service.healthPath()))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String reported = reportedStatus(response.body());

            if (reported != null) {
                // An Actuator service told us plainly; trust it over the status code.
                health = "UP".equalsIgnoreCase(reported) ? ServiceStatus.Health.UP : ServiceStatus.Health.DOWN;
                detail = reported;
            } else if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // No parseable status body - a plain static site such as the UI. 2xx is all we get.
                health = ServiceStatus.Health.UP;
                detail = "HTTP " + response.statusCode();
            } else {
                health = ServiceStatus.Health.DOWN;
                detail = "HTTP " + response.statusCode();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            health = ServiceStatus.Health.UNKNOWN;
            detail = "probe interrupted";
        } catch (Exception e) {
            // Refused, timed out, unresolvable host: from an operator's seat this is "not serving".
            health = ServiceStatus.Health.DOWN;
            detail = describe(e);
            log.debug("Health probe failed for {}: {}", service.id(), e.toString());
        }

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new ServiceStatus(
                service.id(),
                service.name(),
                service.role(),
                service.description(),
                service.externalUrl(),
                health,
                detail,
                latencyMs,
                resolveLinks(service));
    }

    /** The {@code status} field of an Actuator health body, or null if the body isn't one. */
    private String reportedStatus(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode status = objectMapper.readTree(body).get("status");
            return status != null && status.isTextual() ? status.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<ServiceStatus.ResolvedLink> resolveLinks(AdminProperties.ManagedService service) {
        String base = trimTrailingSlash(service.externalUrl());
        return service.links().stream()
                .map(l -> new ServiceStatus.ResolvedLink(l.label(), base + l.path(), l.description()))
                .toList();
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String describe(Exception e) {
        // Connect and response timeouts have different causes and different fixes, and they fire on
        // different clocks (the client's connectTimeout vs this probe's request timeout), so they
        // must not be collapsed into one message quoting a duration that may not be the one that
        // actually elapsed. HttpConnectTimeoutException extends HttpTimeoutException - test it first.
        if (e instanceof java.net.http.HttpConnectTimeoutException) {
            return "connect timed out";
        }
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "no response within " + PROBE_TIMEOUT.toSeconds() + "s";
        }
        if (e instanceof java.net.ConnectException) {
            return "connection refused";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
