package com.removerr.integration.radarr.dto;

import java.util.List;

public record RadarrMovie(
        int id,
        String title,
        int year,
        int tmdbId,
        boolean hasFile,
        long sizeOnDisk,
        String added,
        String path,
        boolean monitored,
        List<RadarrImage> images,
        RadarrMovieFile movieFile   // null when hasFile=false
) {
    public String posterUrl() {
        if (images == null) return null;
        return images.stream()
                .filter(i -> "poster".equals(i.coverType()))
                .map(RadarrImage::remoteUrl)
                .findFirst()
                .orElse(null);
    }
}
