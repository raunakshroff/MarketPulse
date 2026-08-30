package com.marketpulse.admin.model;

import java.util.List;

/**
 * One service as the console shows it: its static description from configuration, plus the result
 * of a live health probe.
 *
 * @param id          stable slug from configuration
 * @param name        display name
 * @param role        one-line positioning
 * @param description what the service is for
 * @param externalUrl browser-reachable base URL
 * @param health      probe outcome
 * @param detail      human-readable probe detail (reported status, HTTP code, or failure reason)
 * @param latencyMs   how long the probe took, or -1 when it was not attempted
 * @param links       operational links, already resolved to absolute URLs
 */
public record ServiceStatus(
        String id,
        String name,
        String role,
        String description,
        String externalUrl,
        Health health,
        String detail,
        long latencyMs,
        List<ResolvedLink> links) {

    /** UNKNOWN covers "probe not attempted or gave an answer we cannot classify", not "down". */
    public enum Health {
        UP,
        DOWN,
        UNKNOWN
    }

    /**
     * @param label       link text
     * @param url         absolute, browser-reachable URL
     * @param description why an operator would click it
     */
    public record ResolvedLink(String label, String url, String description) {}
}
