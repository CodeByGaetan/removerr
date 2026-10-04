package com.removerr.auth.dto;

// Entry of GET https://plex.tv/api/v2/home/users — restricted = managed user
public record PlexHomeUser(long id, boolean restricted) {}
