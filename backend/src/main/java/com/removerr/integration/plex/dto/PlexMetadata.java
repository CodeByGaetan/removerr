package com.removerr.integration.plex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlexMetadata(
        String ratingKey,
        String title,
        int year,
        String thumb,
        int viewCount,
        int viewedLeafCount,   // watched episodes (shows); 0 for movies
        int leafCount,         // total episodes (shows); 0 for movies
        @JsonProperty("Guid") List<PlexGuid> guids
) {
    public Integer tmdbId() {
        if (guids == null) return null;
        return guids.stream()
                .map(PlexGuid::tmdbId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    public Integer tvdbId() {
        if (guids == null) return null;
        return guids.stream()
                .map(PlexGuid::tvdbId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }
}
