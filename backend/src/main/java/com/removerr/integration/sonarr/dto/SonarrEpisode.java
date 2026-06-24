package com.removerr.integration.sonarr.dto;

public record SonarrEpisode(
        int id,
        int seriesId,
        int seasonNumber,
        int episodeNumber,
        boolean monitored,
        Integer episodeFileId
) {}
