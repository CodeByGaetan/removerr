package com.removerr.auth.dto;

// Entry of GET https://plex.tv/api/v2/home/users — restricted = managed user,
// guest = Plex Home's built-in guest profile (not a person)
public record PlexHomeUser(long id, boolean restricted, boolean guest) {}
