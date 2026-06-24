package com.removerr.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlexPin(
        long id,
        String code,
        @JsonProperty("authToken") String authToken,
        @JsonProperty("expiresAt") String expiresAt
) {}
