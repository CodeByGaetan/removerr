package com.removerr.setting.dto;

public record UpdateSettingsRequest(
        String plexServerUrl,

        String radarrUrl,
        String radarrApiKey,       // null = keep existing

        String sonarrUrl,
        String sonarrApiKey,

        String seerrUrl,
        String seerrApiKey,

        Integer trashRetentionDays
) {}
