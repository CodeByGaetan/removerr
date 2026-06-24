package com.removerr.integration.plex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlexHistoryContainer(
        @JsonProperty("MediaContainer") Container mediaContainer
) {
    public record Container(
            @JsonProperty("Metadata") List<PlexHistoryEntry> metadata
    ) {}
}
