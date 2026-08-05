package dev.vedaaxis.api.timeline;

import dev.vedaaxis.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
final class HttpMSpecSourceClient implements MSpecSourceClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    HttpMSpecSourceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public JsonNode fetchJson(URI uri, int maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "VedaAxis/0.1.5 timeline reference importer")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw upstream("M-Spec 数据源返回 HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) {
                    throw upstream("M-Spec 数据超过安全上限");
                }
                return objectMapper.readTree(bytes);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstream("M-Spec 请求已中断");
        } catch (JacksonException exception) {
            throw upstream("M-Spec 返回的 JSON 无法解析");
        } catch (IOException exception) {
            throw upstream("无法连接 M-Spec 数据源");
        }
    }

    private ApiException upstream(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "MSPEC_UPSTREAM_ERROR", message);
    }
}
