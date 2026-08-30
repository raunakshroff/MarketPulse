package com.marketpulse.refdata.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketpulse.refdata.config.NseProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NseHttpClientTest {

    private static final String BASE_URL = "https://www.nseindia.com";

    private HttpClient httpClient;
    private HttpResponse<Object> initResponse;
    private HttpResponse<Object> downloadResponse;
    private NseProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpClient = mock(HttpClient.class);
        initResponse = mock(HttpResponse.class);
        downloadResponse = mock(HttpResponse.class);

        properties = new NseProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setArchiveUrl("https://nsearchives.nseindia.com/products/content/");
        properties.setHeaders(Map.of("User-Agent", "test-agent"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializesSessionByHittingBaseUrl() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse<?>) initResponse);

        new NseHttpClient(httpClient, properties);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo(BASE_URL);
        assertThat(captor.getValue().headers().firstValue("User-Agent")).contains("test-agent");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadsFileSuccessfully() throws Exception {
        when(downloadResponse.statusCode()).thenReturn(200);
        when(downloadResponse.body()).thenReturn("file_content".getBytes(StandardCharsets.UTF_8));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    return request.uri().toString().equals(BASE_URL) ? initResponse : downloadResponse;
                });

        NseHttpClient client = new NseHttpClient(httpClient, properties);
        byte[] content = client.downloadFile("https://dummyurl.com/file.csv");

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("file_content");
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadThrowsNseHttpExceptionOn404() throws Exception {
        when(downloadResponse.statusCode()).thenReturn(404);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    return request.uri().toString().equals(BASE_URL) ? initResponse : downloadResponse;
                });

        NseHttpClient client = new NseHttpClient(httpClient, properties);

        assertThatThrownBy(() -> client.downloadFile("https://dummyurl.com/file.csv"))
                .isInstanceOf(NseHttpException.class)
                .satisfies(e -> assertThat(((NseHttpException) e).getStatusCode()).isEqualTo(404));
    }
}
