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
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
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

    public List<PlexHistoryEntry> getViewHistory(int limit) {
        PlexHistoryContainer response = client.get()
                .uri(serverUrl() + "/status/sessions/history/all?sort=viewedAt:asc" +
                     "&X-Plex-Container-Start=0&X-Plex-Container-Size=" + limit)
                .header("X-Plex-Token", adminToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        Mono.error(new IntegrationException("Plex auth error " + resp.statusCode())))
                .bodyToMono(PlexHistoryContainer.class)
                .block();
        if (response == null || response.mediaContainer() == null) return List.of();
        var entries = response.mediaContainer().metadata();
        return entries != null ? entries : List.of();
    }

    private PlexMediaContainer get(String path) {
        return client.get()
                .uri(serverUrl() + path)
                .header("X-Plex-Token", adminToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        Mono.error(new IntegrationException("Plex auth error " + resp.statusCode())))
                .bodyToMono(PlexMediaContainer.class)
                .block();
    }

    private String serverUrl() {
        String url = settings.get(AppSettingKey.PLEX_SERVER_URL);
        if (url == null || url.isBlank()) throw new ServiceNotConfiguredException(AppSettingKey.PLEX_SERVER_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String adminToken() {
        return plexUserRepository.findByAdminTrue()
                .map(u -> encryptor.decrypt(u.getPlexTokenEncrypted()))
                .orElseThrow(() -> new IntegrationException("No admin user found"));
    }
}
