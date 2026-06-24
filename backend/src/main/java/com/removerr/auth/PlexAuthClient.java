package com.removerr.auth;

import com.removerr.auth.dto.PlexPin;
import com.removerr.auth.dto.PlexUserAccount;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Component
public class PlexAuthClient {

    private static final String PLEX_TV_BASE  = "https://plex.tv";
    private static final String PLEX_PRODUCT  = "Removerr";
    private static final String PLEX_PLATFORM = "Web";
    private static final String PLEX_VERSION  = "1.0";

    private final WebClient plexClient;
    private final AppSettingService settingService;

    public PlexAuthClient(AppSettingService settingService) {
        this.settingService = settingService;
        this.plexClient = WebClient.builder()
                .baseUrl(PLEX_TV_BASE)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-Plex-Product", PLEX_PRODUCT)
                .defaultHeader("X-Plex-Platform", PLEX_PLATFORM)
                .defaultHeader("X-Plex-Version", PLEX_VERSION)
                .build();
    }

    public PlexPin createPin() {
        // strong=true is REQUIRED by the app.plex.tv/auth deep-link flow used in
        // buildAuthUrl(); a short code returns 403 "action not available for this
        // device" (code 1068) there. The trade-off: the code is a long random
        // string, so it must NOT be shown as a typeable PIN in the UI.
        return plexClient.post()
                .uri("/api/v2/pins?strong=true")
                .header("X-Plex-Client-Identifier", getOrCreateClientId())
                .retrieve()
                .bodyToMono(PlexPin.class)
                .block();
    }

    public PlexPin checkPin(long pinId) {
        try {
            return plexClient.get()
                    .uri("/api/v2/pins/{id}", pinId)
                    .header("X-Plex-Client-Identifier", getOrCreateClientId())
                    .retrieve()
                    .bodyToMono(PlexPin.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            return null;
        }
    }

    public PlexUserAccount getUserInfo(String plexToken) {
        PlexUserAccount account = plexClient.get()
                .uri("/api/v2/user")
                .header("X-Plex-Token", plexToken)
                .retrieve()
                .bodyToMono(PlexUserAccount.class)
                .block();

        if (account == null) {
            throw new IllegalStateException("Empty user account response from Plex");
        }
        return account;
    }

    public String buildAuthUrl(String code) {
        String clientId = getOrCreateClientId();
        return "https://app.plex.tv/auth#?clientID=" + clientId
                + "&code=" + code
                + "&context[device][product]=" + PLEX_PRODUCT
                + "&context[device][platform]=" + PLEX_PLATFORM;
    }

    private String getOrCreateClientId() {
        String clientId = settingService.get(AppSettingKey.PLEX_CLIENT_ID);
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
            settingService.set(AppSettingKey.PLEX_CLIENT_ID, clientId);
        }
        return clientId;
    }
}
