package com.removerr.setting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateSettingsRequest(
        String plexServerUrl,

        String radarrUrl,
        String radarrApiKey,       // null = keep existing

        String sonarrUrl,
        String sonarrApiKey,

        String seerrUrl,
        String seerrApiKey,

        @Min(1) @Max(365) Integer trashRetentionDays
) {}
