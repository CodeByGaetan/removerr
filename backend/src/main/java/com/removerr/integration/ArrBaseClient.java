package com.removerr.integration;

import com.removerr.setting.AppSettingService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ArrBaseClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    protected final AppSettingService settings;
    private final WebClient client;
    private final String urlKey;
    private final String apiKeyKey;

    protected ArrBaseClient(AppSettingService settings, String urlKey, String apiKeyKey) {
        this.settings = settings;
        this.urlKey = urlKey;
        this.apiKeyKey = apiKeyKey;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(10, TimeUnit.SECONDS)))
                .responseTimeout(TIMEOUT);

        this.client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    protected <T> T get(String path, Class<T> responseType) {
        return execute(errorHandled(
                client.get().uri(baseUrl() + path).header("X-Api-Key", apiKey()).retrieve())
                .bodyToMono(responseType));
    }

    // Returns null instead of throwing when the resource is not found (HTTP 404).
    protected <T> T getOrNull(String path, Class<T> responseType) {
        return execute(errorHandled(
                client.get().uri(baseUrl() + path).header("X-Api-Key", apiKey()).retrieve()
                        .onStatus(status -> status.value() == 404, resp -> Mono.empty()))
                .bodyToMono(responseType));
    }

    protected <T> List<T> getList(String path, ParameterizedTypeReference<List<T>> typeRef) {
        return execute(errorHandled(
                client.get().uri(baseUrl() + path).header("X-Api-Key", apiKey()).retrieve())
                .bodyToMono(typeRef));
    }

    protected void delete(String path) {
        execute(errorHandled(
                client.delete().uri(baseUrl() + path).header("X-Api-Key", apiKey()).retrieve())
                .toBodilessEntity());
    }

    protected <T> void delete(String path, T body) {
        execute(errorHandled(
                client.method(HttpMethod.DELETE).uri(baseUrl() + path)
                        .header("X-Api-Key", apiKey()).bodyValue(body).retrieve())
                .toBodilessEntity());
    }

    protected <T, R> R post(String path, T body, Class<R> responseType) {
        return execute(errorHandled(
                client.post().uri(baseUrl() + path)
                        .header("X-Api-Key", apiKey()).bodyValue(body).retrieve())
                .bodyToMono(responseType));
    }

    protected <T> void post(String path, T body) {
        execute(errorHandled(
                client.post().uri(baseUrl() + path)
                        .header("X-Api-Key", apiKey()).bodyValue(body).retrieve())
                .toBodilessEntity());
    }

    protected <T> void put(String path, T body) {
        execute(errorHandled(
                client.put().uri(baseUrl() + path)
                        .header("X-Api-Key", apiKey()).bodyValue(body).retrieve())
                .toBodilessEntity());
    }

    // ── Shared error handling ─────────────────────────────────────────────────

    // Map any 4xx/5xx to an IntegrationException so the global handler reports a
    // 502 upstream failure rather than a generic 500.
    private static WebClient.ResponseSpec errorHandled(WebClient.ResponseSpec spec) {
        return spec
                .onStatus(HttpStatusCode::is4xxClientError, ArrBaseClient::toIntegrationError)
                .onStatus(HttpStatusCode::is5xxServerError, ArrBaseClient::toIntegrationError);
    }

    private static Mono<? extends Throwable> toIntegrationError(ClientResponse resp) {
        return Mono.error(new IntegrationException(
                "Request failed: " + resp.request().getMethod() + " → HTTP " + resp.statusCode().value()));
    }

    // WebClientRequestException signals a transport failure (host down, DNS,
    // timeout) — wrap it so it joins the same IntegrationException path.
    private <T> T execute(Mono<T> mono) {
        try {
            return mono.block();
        } catch (WebClientRequestException e) {
            throw new IntegrationException("Service unreachable: " + baseUrl(), e);
        }
    }

    private String baseUrl() {
        String url = settings.get(urlKey);
        if (url == null || url.isBlank()) {
            throw new ServiceNotConfiguredException(urlKey);
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String apiKey() {
        String key = settings.get(apiKeyKey);
        if (key == null || key.isBlank()) {
            throw new ServiceNotConfiguredException(apiKeyKey);
        }
        return key;
    }
}
