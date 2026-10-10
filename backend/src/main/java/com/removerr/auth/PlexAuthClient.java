package com.removerr.auth;

import com.removerr.auth.dto.PlexHomeUser;
import com.removerr.auth.dto.PlexHomeUsersResponse;
import com.removerr.auth.dto.PlexPin;
import com.removerr.auth.dto.PlexSharedServer;
import com.removerr.auth.dto.PlexUserAccount;
import com.removerr.integration.IntegrationException;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * plex.tv ids of the users with an accepted share on the given server. The server's own
     * /accounts can't answer this: it keeps every account that ever had history on it.
     */
    public Map<Long, String> getSharedUsers(String ownerToken, String machineIdentifier) {
        List<PlexSharedServer> shares = plexClient.get()
                .uri("/api/v2/shared_servers/owned/accepted")
                .header("X-Plex-Token", ownerToken)
                .header("X-Plex-Client-Identifier", getOrCreateClientId())
                .retrieve()
                .bodyToFlux(PlexSharedServer.class)
                .collectList()
                .block();
        if (shares == null) {
            throw new IllegalStateException("Empty shared servers response from Plex");
        }
        return sharedUsersFor(shares, machineIdentifier);
    }

    /**
     * Active shares of the given server, as plex.tv id → name. Shares that exist but none for
     * this server means its machine identifier changed (reinstall, migration): treating that
     * as "nobody has access" would strip every user's role, so it fails instead.
     */
    static Map<Long, String> sharedUsersFor(List<PlexSharedServer> shares, String machineIdentifier) {
        List<PlexSharedServer> active = shares.stream()
                .filter(s -> s.deletedAt() == null && s.leftAt() == null)
                .toList();
        Map<Long, String> users = new LinkedHashMap<>();
        active.stream()
                .filter(s -> machineIdentifier.equals(s.machineIdentifier()))
                .forEach(s -> users.putIfAbsent(s.invitedId(), s.invitedName()));
        if (users.isEmpty() && !active.isEmpty()) {
            throw new IntegrationException("plex.tv lists " + active.size()
                    + " share(s), none for server " + machineIdentifier + " — did its identifier change?");
        }
        return users;
    }

    /**
     * Members of the owner's Plex Home: they reach the server through the Home, not through a
     * share, so they never appear in shared_servers. The owner is listed too.
     */
    public List<PlexHomeUser> getHomeUsers(String ownerToken) {
        PlexHomeUsersResponse response = plexClient.get()
                .uri("/api/v2/home/users")
                .header("X-Plex-Token", ownerToken)
                .header("X-Plex-Client-Identifier", getOrCreateClientId())
                .retrieve()
                .bodyToMono(PlexHomeUsersResponse.class)
                .block();
        if (response == null || response.users() == null) {
            throw new IllegalStateException("Empty Plex Home response from Plex");
        }
        return response.users();
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
