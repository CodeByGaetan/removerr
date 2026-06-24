package com.removerr.auth.dto;

// Flat response from GET https://plex.tv/api/v2/user
public record PlexUserAccount(
        long id,
        String username,
        String email,
        String thumb
) {}
