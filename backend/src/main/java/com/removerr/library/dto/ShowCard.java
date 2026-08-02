package com.removerr.library.dto;

import java.util.List;

public record ShowCard(
        String mediaType,           // always "show"
        int sonarrId,
        int tvdbId,
        String title,
        int year,
        String posterUrl,
        long sizeOnDisk,
        boolean monitored,
        String addedAt,
        List<SeasonCard> seasons,
        SeerrInfo seerr,
        Integer plexWatchedEpisodes,    // null if Plex not configured
        Integer plexTotalEpisodes,      // null if Plex not configured
        List<Long> plexViewerUserIds    // null if Plex not configured; otherwise local PlexUser IDs of counted users who watched the last episode of the last visible season
) {}
