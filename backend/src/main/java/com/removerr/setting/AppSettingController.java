package com.removerr.setting;

import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.job.JobRunner;
import com.removerr.plexuser.UserSyncJob;
import com.removerr.setting.dto.ConnectionTestResponse;
import com.removerr.setting.dto.ConnectionTestResponse.ServiceStatus;
import com.removerr.setting.dto.SettingsResponse;
import com.removerr.setting.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@RestController
@RequestMapping("/api/settings")
public class AppSettingController {

    private final AppSettingService settingService;
    private final PlexLibraryClient plexLibraryClient;
    private final JobRunner jobRunner;

    public AppSettingController(AppSettingService settingService, PlexLibraryClient plexLibraryClient,
                                JobRunner jobRunner) {
        this.settingService = settingService;
        this.plexLibraryClient = plexLibraryClient;
        this.jobRunner = jobRunner;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingService.getSettings();
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
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
     * Same semantics as {@link #updateSettings}: a null field falls back to the saved value.
     */
    @PostMapping("/test")
    public ConnectionTestResponse testConnections(@RequestBody UpdateSettingsRequest form) {
        return new ConnectionTestResponse(
                testService(formOrSaved(form.plexServerUrl(), AppSettingKey.PLEX_SERVER_URL),
                        "/library/sections", "X-Plex-Token", plexLibraryClient.findOwnerToken().orElse(null)),
                testService(formOrSaved(form.radarrUrl(), AppSettingKey.RADARR_URL),
                        "/api/v3/system/status", "X-Api-Key",
                        formOrSaved(form.radarrApiKey(), AppSettingKey.RADARR_API_KEY)),
                testService(formOrSaved(form.sonarrUrl(), AppSettingKey.SONARR_URL),
                        "/api/v3/system/status", "X-Api-Key",
                        formOrSaved(form.sonarrApiKey(), AppSettingKey.SONARR_API_KEY)),
                testService(formOrSaved(form.seerrUrl(), AppSettingKey.SEERR_URL),
                        "/api/v1/auth/me", "X-Api-Key",
                        formOrSaved(form.seerrApiKey(), AppSettingKey.SEERR_API_KEY))
        );
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
