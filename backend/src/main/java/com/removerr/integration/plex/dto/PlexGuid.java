package com.removerr.integration.plex.dto;

public record PlexGuid(String id) {

    public boolean isTmdb() { return id != null && id.startsWith("tmdb://"); }
    public boolean isTvdb() { return id != null && id.startsWith("tvdb://"); }

    public Integer tmdbId() {
        if (!isTmdb()) return null;
        try { return Integer.parseInt(id.substring(7)); } catch (NumberFormatException e) { return null; }
    }

    public Integer tvdbId() {
        if (!isTvdb()) return null;
        try { return Integer.parseInt(id.substring(7)); } catch (NumberFormatException e) { return null; }
    }
}
