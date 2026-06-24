package com.removerr.integration.plex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlexAccountEntry(
        @JsonProperty("id")    int id,
        @JsonProperty("name")  String name,
        @JsonProperty("thumb") String thumb
) {}
