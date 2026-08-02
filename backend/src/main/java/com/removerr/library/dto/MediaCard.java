package com.removerr.library.dto;

import java.util.List;

public record MediaCard(
        String mediaType,   // "movie" or "show"
        int radarrId,
        int tmdbId,
        String title,
        int year,
        String posterUrl,
        long sizeOnDisk,
        boolean hasFile,
        boolean monitored,
        String addedAt,
        SeerrInfo seerr,        // null if not requested via Seerr
        List<Long> plexViewerUserIds  // null if Plex not configured; otherwise local PlexUser IDs of counted users who watched
) {}
