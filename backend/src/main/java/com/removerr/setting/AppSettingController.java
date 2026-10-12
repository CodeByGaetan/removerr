package com.removerr.setting;

import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.job.JobRunner;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.UserSyncJob;
import com.removerr.setting.dto.ConnectionTestResponse;
import com.removerr.setting.dto.ConnectionTestResponse.ServiceStatus;
import com.removerr.setting.dto.SettingsResponse;
import com.removerr.setting.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@RestController
@RequestMapping("/api/settings")
public class AppSettingController {

    private final AppSettingService settingService;
    private final PlexLibraryClient plexLibraryClient;
    private final JobRunner jobRunner;
    private final PlexUserRepository plexUserRepository;

    public AppSettingController(AppSettingService settingService, PlexLibraryClient plexLibraryClient,
                                JobRunner jobRunner, PlexUserRepository plexUserRepository) {
        this.settingService = settingService;
        this.plexLibraryClient = plexLibraryClient;
        this.jobRunner = jobRunner;
        this.plexUserRepository = plexUserRepository;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingService.getSettings();
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@Valid @RequestBody UpdateSettingsRequest request,
                                               @AuthenticationPrincipal Long userId) {
        if (!isOwner(userId)) rejectSecretRedirection(request);
        String previousPlexUrl = settingService.get(AppSettingKey.PLEX_SERVER_URL);
        settingService.updateSettings(request);
        if (!Objects.equals(previousPlexUrl, settingService.get(AppSettingKey.PLEX_SERVER_URL))) {
            syncUsersNow();
        }
        return ResponseEntity.noContent().build();
    }

    // A new Plex server means new accounts and shares; on a fresh install it's also the first
    // moment a sync can succeed. Waiting for the hourly run would leave the Users tab empty.
    private void syncUsersNow() {
        try {
            jobRunner.runInBackground(UserSyncJob.KEY, null);
        } catch (IllegalStateException alreadyRunning) {
            // The running sync may predate the change, but the next hourly run will catch up.
        }
    }

    /**
     * Tests the values currently in the form so they can be validated before saving.
     * Same semantics as {@link #updateSettings}: a null field falls back to the saved value —
     * except that saved secrets only go to their saved URL unless the caller is the owner.
     */
    @PostMapping("/test")
    public ConnectionTestResponse testConnections(@RequestBody UpdateSettingsRequest form,
                                                  @AuthenticationPrincipal Long userId) {
        boolean owner = isOwner(userId);
        String plexUrl = formOrSaved(form.plexServerUrl(), AppSettingKey.PLEX_SERVER_URL);
        String plexToken = owner || isSavedUrl(plexUrl, AppSettingKey.PLEX_SERVER_URL)
                ? plexLibraryClient.findOwnerToken().orElse(null) : null;
        return new ConnectionTestResponse(
                testService(plexUrl, "/library/sections", "X-Plex-Token", plexToken),
                testArr(form.radarrUrl(), form.radarrApiKey(), AppSettingKey.RADARR_URL,
                        AppSettingKey.RADARR_API_KEY, "/api/v3/system/status", owner),
                testArr(form.sonarrUrl(), form.sonarrApiKey(), AppSettingKey.SONARR_URL,
                        AppSettingKey.SONARR_API_KEY, "/api/v3/system/status", owner),
                testArr(form.seerrUrl(), form.seerrApiKey(), AppSettingKey.SEERR_URL,
                        AppSettingKey.SEERR_API_KEY, "/api/v1/auth/me", owner)
        );
    }

    private ServiceStatus testArr(String formUrl, String formKey, String urlKey, String apiKeyKey,
                                  String path, boolean owner) {
        String url = formOrSaved(formUrl, urlKey);
        String key = formKey;
        if (key == null && (owner || isSavedUrl(url, urlKey))) key = settingService.get(apiKeyKey);
        return testService(url, path, "X-Api-Key", key);
    }

    // Saved secrets are write-only and the owner's plex.tv token controls their whole Plex
    // account: a non-owner admin must not be able to send them to a server of their choice,
    // neither through the test above nor by saving a new URL that the next call would use.
    private void rejectSecretRedirection(UpdateSettingsRequest req) {
        if (changesUrl(req.plexServerUrl(), AppSettingKey.PLEX_SERVER_URL)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can change the Plex server URL");
        }
        requireKeyWithNewUrl(req.radarrUrl(), req.radarrApiKey(), AppSettingKey.RADARR_URL, AppSettingKey.RADARR_API_KEY);
        requireKeyWithNewUrl(req.sonarrUrl(), req.sonarrApiKey(), AppSettingKey.SONARR_URL, AppSettingKey.SONARR_API_KEY);
        requireKeyWithNewUrl(req.seerrUrl(), req.seerrApiKey(), AppSettingKey.SEERR_URL, AppSettingKey.SEERR_API_KEY);
    }

    private void requireKeyWithNewUrl(String formUrl, String formKey, String urlKey, String apiKeyKey) {
        if (changesUrl(formUrl, urlKey) && formKey == null && settingService.get(apiKeyKey) != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The API key must be entered again when its URL changes");
        }
    }

    private boolean changesUrl(String formUrl, String urlKey) {
        return formUrl != null && !isSavedUrl(formUrl, urlKey);
    }

    private boolean isSavedUrl(String url, String urlKey) {
        return url != null && normalize(url).equalsIgnoreCase(normalize(settingService.get(urlKey)));
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private boolean isOwner(Long userId) {
        return userId != null && plexUserRepository.findById(userId).map(PlexUser::isOwner).orElse(false);
    }

    private String formOrSaved(String formValue, String key) {
        return formValue != null ? formValue : settingService.get(key);
    }

    private ServiceStatus testService(String url, String path, String authHeader, String credential) {
        if (isBlank(url) || isBlank(credential)) return ServiceStatus.notConfigured();
        try {
            Integer statusCode = WebClient.create(url)
                    .get()
                    .uri(path)
                    .header(authHeader, credential)
                    .exchangeToMono(resp -> Mono.just(resp.statusCode().value()))
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (statusCode == null) return ServiceStatus.unreachable("No response");
            if (statusCode == 401 || statusCode == 403) return ServiceStatus.authFailed();
            if (statusCode >= 200 && statusCode < 300) return ServiceStatus.ok();
            return ServiceStatus.unreachable("HTTP " + statusCode);
        } catch (WebClientRequestException e) {
            return ServiceStatus.unreachable(rootCauseMessage(e));
        } catch (Exception e) {
            return ServiceStatus.unreachable(e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }
}
