package com.marketpulse.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketpulse.admin.model.ServiceStatus;
import com.marketpulse.admin.service.ServiceStatusService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
        })
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceStatusService serviceStatusService;

    private static ServiceStatus serviceStatus(String id, ServiceStatus.Health health) {
        return new ServiceStatus(
                id,
                id,
                "Read / query",
                "Test description",
                "http://localhost:8082",
                health,
                health.name(),
                12,
                List.of(new ServiceStatus.ResolvedLink(
                        "Swagger UI", "http://localhost:8082/swagger-ui.html", "docs")));
    }

    @Test
    void listsEveryConfiguredService() throws Exception {
        when(serviceStatusService.getAll()).thenReturn(List.of(
                serviceStatus("markets-ref-data", ServiceStatus.Health.UP),
                serviceStatus("stock-discovery", ServiceStatus.Health.DOWN)));

        mockMvc.perform(get("/api/v1/admin/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("markets-ref-data"))
                .andExpect(jsonPath("$[0].health").value("UP"))
                .andExpect(jsonPath("$[1].health").value("DOWN"))
                .andExpect(jsonPath("$[0].links[0].url").value("http://localhost:8082/swagger-ui.html"));
    }

    @Test
    void stillReturnsOkWhenAServiceIsDown() throws Exception {
        // A DOWN service is data to report, not a failure of this endpoint.
        when(serviceStatusService.getAll())
                .thenReturn(List.of(serviceStatus("stock-discovery", ServiceStatus.Health.DOWN)));

        mockMvc.perform(get("/api/v1/admin/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].health").value("DOWN"));
    }

    @Test
    void returnsAnEmptyArrayWhenNothingIsConfigured() throws Exception {
        when(serviceStatusService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getsASingleServiceById() throws Exception {
        when(serviceStatusService.getById("stock-discovery"))
                .thenReturn(Optional.of(serviceStatus("stock-discovery", ServiceStatus.Health.UP)));

        mockMvc.perform(get("/api/v1/admin/services/stock-discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stock-discovery"))
                .andExpect(jsonPath("$.health").value("UP"));
    }

    @Test
    void returnsNotFoundForAnUnknownServiceId() throws Exception {
        when(serviceStatusService.getById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/services/nope"))
                .andExpect(status().isNotFound());
    }
}
