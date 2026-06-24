package com.removerr.integration.sonarr.dto;

public record SonarrSeasonStats(
        int episodeFileCount,    // files actually downloaded
        int episodeCount,        // monitored AND aired episodes
        int totalEpisodeCount,   // all episodes announced (incl. future + unmonitored)
        long sizeOnDisk
) {}
