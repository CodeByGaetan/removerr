package com.removerr.integration.plex.dto;

// type is "movie" or "show"
public record PlexSection(String key, String title, String type) {}
