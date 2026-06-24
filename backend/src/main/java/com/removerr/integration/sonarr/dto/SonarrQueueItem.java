package com.removerr.integration.sonarr.dto;

public record SonarrQueueItem(
        int id,
        Integer seriesId,
        Integer episodeId,
        Integer seasonNumber
) {}
