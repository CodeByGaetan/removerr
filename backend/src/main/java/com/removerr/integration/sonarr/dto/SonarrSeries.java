package com.removerr.integration.sonarr.dto;

import java.util.List;

public record SonarrSeries(
        int id,
        String title,
        int year,
        int tvdbId,
        String path,
        long sizeOnDisk,
        boolean monitored,
        String added,
        List<SonarrSeason> seasons,
        List<SonarrImage> images
) {
    public String posterUrl() {
        if (images == null) return null;
        return images.stream()
                .filter(i -> "poster".equals(i.coverType()))
                .map(SonarrImage::remoteUrl)
                .findFirst()
                .orElse(null);
    }
}
