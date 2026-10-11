package com.removerr.integration.plex;

import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.integration.IntegrationException;
import com.removerr.integration.ServiceNotConfiguredException;
import com.removerr.integration.plex.dto.PlexAccountEntry;
import com.removerr.integration.plex.dto.PlexHistoryContainer;
import com.removerr.integration.plex.dto.PlexHistoryEntry;
import com.removerr.integration.plex.dto.PlexMediaContainer;
import com.removerr.integration.plex.dto.PlexMetadata;
import com.removerr.integration.plex.dto.PlexSection;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class PlexLibraryClient {

    private final WebClient client;
    private final AppSettingService settings;
    private final PlexUserRepository plexUserRepository;
    private final AesGcmEncryptor encryptor;

    public PlexLibraryClient(AppSettingService settings, PlexUserRepository plexUserRepository,
                              AesGcmEncryptor encryptor) {
        this.settings = settings;
        this.plexUserRepository = plexUserRepository;
        this.encryptor = encryptor;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS)))
                .responseTimeout(Duration.ofSeconds(10));

        this.client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-Plex-Product", "Removerr")
                .build();
    }

    public List<PlexSection> getSections() {
        PlexMediaContainer response = get("/library/sections");
        if (response == null || response.mediaContainer() == null) return List.of();
        var dirs = response.mediaContainer().directories();
        return dirs != null ? dirs : List.of();
    }

    public List<PlexMetadata> getMediaItems(String sectionKey) {
        PlexMediaContainer response = get("/library/sections/" + sectionKey + "/all?includeGuids=1");
        if (response == null || response.mediaContainer() == null) return List.of();
        var items = response.mediaContainer().metadata();
        return items != null ? items : List.of();
    }

    public List<PlexAccountEntry> getAccounts() {
        PlexMediaContainer response = get("/accounts");
        if (response == null || response.mediaContainer() == null) return List.of();
        var accounts = response.mediaContainer().accounts();
        return accounts != null ? accounts : List.of();
    }

    public String getMachineIdentifier() {
        PlexMediaContainer response = get("/identity");
        if (response == null || response.mediaContainer() == null
                || response.mediaContainer().machineIdentifier() == null) {
            throw new IntegrationException("Plex server returned no machine identifier");
        }
        return response.mediaContainer().machineIdentifier();
    }

    // Newest first: past the limit, the oldest views are the ones dropped. Ascending order
    // would drop the recent ones and make freshly watched items look safe to delete.
    public List<PlexHistoryEntry> getViewHistory(int limit) {
        PlexHistoryContainer response = fetch("/status/sessions/history/all?sort=viewedAt:desc"
                + "&X-Plex-Container-Start=0&X-Plex-Container-Size=" + limit, PlexHistoryContainer.class);
        if (response == null || response.mediaContainer() == null) return List.of();
        var entries = response.mediaContainer().metadata();
        return entries != null ? entries : List.of();
    }

    private PlexMediaContainer get(String path) {
        return fetch(path, PlexMediaContainer.class);
    }

    // Every failure — HTTP error or Plex unreachable (off, restarting, timeout) — becomes an
    // IntegrationException, which the library treats as "Plex data unavailable" instead of
    // failing the whole page.
    private <T> T fetch(String path, Class<T> responseType) {
        try {
            return client.get()
                    .uri(serverUrl() + path)
                    .header("X-Plex-Token", ownerToken())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            Mono.error(new IntegrationException("Plex request failed: HTTP " + resp.statusCode().value())))
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientRequestException e) {
            throw new IntegrationException("Plex server unreachable: " + e.getMessage(), e);
        }
    }

    private String serverUrl() {
        String url = settings.get(AppSettingKey.PLEX_SERVER_URL);
        if (url == null || url.isBlank()) throw new ServiceNotConfiguredException(AppSettingKey.PLEX_SERVER_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public Optional<String> findOwnerToken() {
        return plexUserRepository.findByOwnerTrue()
                .map(u -> encryptor.decrypt(u.getPlexTokenEncrypted()));
    }

    private String ownerToken() {
        return findOwnerToken().orElseThrow(() -> new IntegrationException("No owner user found"));
    }
}
