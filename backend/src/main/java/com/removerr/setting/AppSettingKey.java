package com.removerr.setting;

import java.util.Set;

public final class AppSettingKey {

    public static final String PLEX_SERVER_URL       = "plex.server.url";

    public static final String RADARR_URL            = "radarr.url";
    public static final String RADARR_API_KEY        = "radarr.api_key_encrypted";

    public static final String SONARR_URL            = "sonarr.url";
    public static final String SONARR_API_KEY        = "sonarr.api_key_encrypted";

    public static final String SEERR_URL         = "seerr.url";
    public static final String SEERR_API_KEY     = "seerr.api_key_encrypted";

    public static final String TRASH_RETENTION_DAYS  = "trash.retention_days";

    public static final String PLEX_CLIENT_ID        = "plex.client_id";

    // Secret keys: their values are AES-encrypted at rest. A key must be listed
    // here to be encrypted — the name is not parsed, so this is the only place
    // that decides it.
    public static final Set<String> ENCRYPTED = Set.of(
            RADARR_API_KEY, SONARR_API_KEY, SEERR_API_KEY);

    private AppSettingKey() {}
}
