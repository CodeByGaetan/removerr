package com.removerr.integration.sonarr.dto;

public record SonarrEpisodeFile(int id, int seriesId, int seasonNumber, String path, long size) {}
