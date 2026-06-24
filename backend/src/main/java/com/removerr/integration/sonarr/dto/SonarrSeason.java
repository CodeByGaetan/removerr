package com.removerr.integration.sonarr.dto;

public record SonarrSeason(int seasonNumber, boolean monitored, SonarrSeasonStats statistics) {

    // A season is "visible" if it is not a specials season (0) and is either monitored
    // or already has at least one downloaded episode. Specials and seasons that nobody
    // asked for (not monitored, 0 files) are hidden everywhere in Removerr.
    public boolean isVisible() {
        return seasonNumber > 0
                && (monitored || (statistics != null && statistics.episodeFileCount() > 0));
    }
}
