package com.removerr.library.dto;

import java.util.List;

public record SeasonCard(
        int seasonNumber,
        int episodeFileCount,
        int totalEpisodes,
        long sizeOnDisk,
        boolean monitored,
        List<Long> plexViewerUserIds   // null if Plex not configured; otherwise local PlexUser IDs of counted users who watched the last episode
) {}
