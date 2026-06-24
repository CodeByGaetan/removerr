package com.removerr.integration.plex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Root wrapper for all Plex API responses
public record PlexMediaContainer(
        @JsonProperty("MediaContainer") Container mediaContainer
) {
    public record Container(
            @JsonProperty("Directory") List<PlexSection> directories,
            @JsonProperty("Metadata")  List<PlexMetadata> metadata,
            @JsonProperty("Account")   List<PlexAccountEntry> accounts
    ) {}
}
