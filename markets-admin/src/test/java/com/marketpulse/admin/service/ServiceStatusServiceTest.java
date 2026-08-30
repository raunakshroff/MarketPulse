package com.marketpulse.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.marketpulse.admin.config.AdminProperties;
import com.marketpulse.admin.model.ServiceStatus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceStatusServiceTest {

    @Mock
    private HttpClient httpClient;

    private AdminProperties properties;

    private static AdminProperties.ManagedService service(String id, String healthPath) {
        return new AdminProperties.ManagedService(
                id,
                id,
                "Test role",
                "Test description",
                "http://internal-" + id + ":8080",
                "http://localhost:9999",
                healthPath,
                List.of(new AdminProperties.Link("Swagger UI", "/swagger-ui.html", "docs")));
    }

    @SuppressWarnings("unchecked")
    private void respondWith(int statusCode, String body) throws Exception {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @BeforeEach
    void setUp() {
        properties = new AdminProperties(true, List.of(service("stock-discovery", "/actuator/health")));
    }

    @Test
    void reportsUpWhenActuatorSaysUp() throws Exception {
        respondWith(200, "{\"status\":\"UP\"}");

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        assertThat(status.health()).isEqualTo(ServiceStatus.Health.UP);
        assertThat(status.detail()).isEqualTo("UP");
        assertThat(status.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void trustsTheReportedStatusOverTheStatusCode() throws Exception {
        // Actuator answers 503 with a DOWN body; the body is the more specific signal.
        respondWith(503, "{\"status\":\"DOWN\"}");

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        assertThat(status.health()).isEqualTo(ServiceStatus.Health.DOWN);
        assertThat(status.detail()).isEqualTo("DOWN");
    }

    @Test
    void treatsAPlainTwoHundredWithNoJsonBodyAsUp() throws Exception {
        // markets-ui is static nginx: no Actuator, so a 200 on / is the only signal available.
        properties = new AdminProperties(true, List.of(service("markets-ui", "/")));
        respondWith(200, "<!doctype html><html><body>hello</body></html>");

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        assertThat(status.health()).isEqualTo(ServiceStatus.Health.UP);
        assertThat(status.detail()).isEqualTo("HTTP 200");
    }

    @Test
    void reportsDownWhenTheServiceIsUnreachable() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.ConnectException("Connection refused"));

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        assertThat(status.health()).isEqualTo(ServiceStatus.Health.DOWN);
        assertThat(status.detail()).isEqualTo("connection refused");
    }

    @Test
    void distinguishesAConnectTimeoutFromAResponseTimeout() throws Exception {
        // These fire on different clocks and have different fixes, so the console must not report
        // one as the other - nor quote a duration that was not the one that actually elapsed.
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpConnectTimeoutException("timed out"));
        assertThat(new ServiceStatusService(properties, httpClient).getAll().get(0).detail())
                .isEqualTo("connect timed out");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("timed out"));
        assertThat(new ServiceStatusService(properties, httpClient).getAll().get(0).detail())
                .isEqualTo("no response within 3s");
    }

    @Test
    void reportsDownOnAnUnparseableErrorResponse() throws Exception {
        respondWith(502, "<html>Bad Gateway</html>");

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        assertThat(status.health()).isEqualTo(ServiceStatus.Health.DOWN);
        assertThat(status.detail()).isEqualTo("HTTP 502");
    }

    @Test
    void resolvesLinksAgainstTheExternalUrlNotTheInternalOne() throws Exception {
        respondWith(200, "{\"status\":\"UP\"}");

        ServiceStatus status = new ServiceStatusService(properties, httpClient).getAll().get(0);

        // The browser follows these, so they must never carry the compose-internal hostname.
        assertThat(status.links()).singleElement().satisfies(link ->
                assertThat(link.url()).isEqualTo("http://localhost:9999/swagger-ui.html"));
    }

    @Test
    void getByIdReturnsEmptyForAnUnknownService() {
        assertThat(new ServiceStatusService(properties, httpClient).getById("nope")).isEmpty();
    }

    @Test
    void anEmptyRegistryProbesNothing() {
        AdminProperties empty = new AdminProperties(true, null);

        assertThat(new ServiceStatusService(empty, httpClient).getAll()).isEmpty();
    }
}
