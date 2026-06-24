package com.removerr.library.dto;

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
        Integer plexViewCount,          // null if Plex not configured; 0 = not watched; >0 = watched
        Integer plexUniqueViewers       // null if Plex not configured; count of counted users who watched
) {}
