package com.removerr.setting.dto;

public record SettingsResponse(
        String plexServerUrl,

        String radarrUrl,
        boolean radarrApiKeyConfigured,

        String sonarrUrl,
        boolean sonarrApiKeyConfigured,

        String seerrUrl,
        boolean seerrApiKeyConfigured,

        int trashRetentionDays
) {}
