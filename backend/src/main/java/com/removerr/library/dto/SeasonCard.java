package com.removerr.library.dto;

public record SeasonCard(
        int seasonNumber,
        int episodeFileCount,
        int totalEpisodes,
        long sizeOnDisk,
        boolean monitored,
        Integer plexUniqueViewers   // null if Plex not configured
) {}
