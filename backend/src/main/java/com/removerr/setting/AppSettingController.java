package com.removerr.setting;

import com.removerr.setting.dto.ConnectionTestResponse;
import com.removerr.setting.dto.ConnectionTestResponse.ServiceStatus;
import com.removerr.setting.dto.SettingsResponse;
import com.removerr.setting.dto.UpdateSettingsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/settings")
public class AppSettingController {

    private final AppSettingService settingService;

    public AppSettingController(AppSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingService.getSettings();
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@RequestBody UpdateSettingsRequest request) {
        settingService.updateSettings(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tests the values currently in the form so they can be validated before saving.
     * Same semantics as {@link #updateSettings}: a null field falls back to the saved value.
     */
    @PostMapping("/test")
    public ConnectionTestResponse testConnections(@RequestBody UpdateSettingsRequest form) {
        return new ConnectionTestResponse(
                testArrService(formOrSaved(form.radarrUrl(), AppSettingKey.RADARR_URL),
                        formOrSaved(form.radarrApiKey(), AppSettingKey.RADARR_API_KEY),
                        "/api/v3/system/status"),
                testArrService(formOrSaved(form.sonarrUrl(), AppSettingKey.SONARR_URL),
                        formOrSaved(form.sonarrApiKey(), AppSettingKey.SONARR_API_KEY),
                        "/api/v3/system/status"),
                testSeerr(formOrSaved(form.seerrUrl(), AppSettingKey.SEERR_URL),
                        formOrSaved(form.seerrApiKey(), AppSettingKey.SEERR_API_KEY))
        );
    }

    private String formOrSaved(String formValue, String key) {
        return formValue != null ? formValue : settingService.get(key);
    }

    private ServiceStatus testArrService(String url, String apiKey, String statusPath) {
        if (isBlank(url) || isBlank(apiKey)) return ServiceStatus.notConfigured();
        try {
            Integer statusCode = WebClient.create(url)
                    .get()
                    .uri(statusPath)
                    .header("X-Api-Key", apiKey)
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

    private ServiceStatus testSeerr(String url, String apiKey) {
        if (isBlank(url) || isBlank(apiKey)) return ServiceStatus.notConfigured();
        try {
            Integer statusCode = WebClient.create(url)
                    .get()
                    .uri("/api/v1/auth/me")
                    .header("X-Api-Key", apiKey)
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
