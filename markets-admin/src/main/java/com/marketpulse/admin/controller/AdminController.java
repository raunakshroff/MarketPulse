package com.marketpulse.admin.controller;

import com.marketpulse.admin.model.ServiceStatus;
import com.marketpulse.admin.service.ServiceStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Operational view of the MarketPulse services")
public class AdminController {

    private final ServiceStatusService serviceStatusService;

    public AdminController(ServiceStatusService serviceStatusService) {
        this.serviceStatusService = serviceStatusService;
    }

    /** Every configured service with a freshly probed health status. */
    @Operation(
            summary = "List every managed service with live health",
            description = """
                    Returns the configured service registry, each entry enriched with a health probe \
                    performed by this service. Probes run server-side deliberately: the browser \
                    cannot reach the compose-internal hostnames, and probing cross-origin from the \
                    console page would be blocked by CORS. Always 200 — an unreachable service is \
                    reported as a DOWN entry in the payload, not an error on this call.""")
    @ApiResponse(responseCode = "200", description = "Service registry with per-service health")
    @GetMapping("/services")
    public List<ServiceStatus> getServices() {
        return serviceStatusService.getAll();
    }

    /** One service by its configured id. */
    @Operation(
            summary = "Get one managed service with live health",
            description = "Same payload as the list endpoint, for a single configured service id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service found and probed"),
            @ApiResponse(responseCode = "404", description = "No service is configured with that id")
    })
    @GetMapping("/services/{id}")
    public ResponseEntity<ServiceStatus> getService(
            @Parameter(description = "Configured service id", example = "stock-discovery")
            @PathVariable String id) {
        return serviceStatusService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
