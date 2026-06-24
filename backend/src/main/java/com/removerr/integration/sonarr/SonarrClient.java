package com.removerr.integration.sonarr;

import com.removerr.integration.ArrBaseClient;
import com.removerr.integration.sonarr.dto.SonarrEpisode;
import com.removerr.integration.sonarr.dto.SonarrEpisodeFile;
import com.removerr.integration.sonarr.dto.SonarrQueueItem;
import com.removerr.integration.sonarr.dto.SonarrQueuePage;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SonarrClient extends ArrBaseClient {

    private static final int QUEUE_PAGE_SIZE = 100;

    public SonarrClient(AppSettingService settings) {
        super(settings, AppSettingKey.SONARR_URL, AppSettingKey.SONARR_API_KEY);
    }

    public List<SonarrSeries> getSeries() {
        return getList("/api/v3/series", new ParameterizedTypeReference<>() {});
    }

    public SonarrSeries getSeries(int id) {
        return get("/api/v3/series/" + id, SonarrSeries.class);
    }

    public List<SonarrEpisodeFile> getEpisodeFiles(int seriesId) {
        return getList("/api/v3/episodefile?seriesId=" + seriesId,
                new ParameterizedTypeReference<>() {});
    }

    public void deleteEpisodeFile(int id) {
        delete("/api/v3/episodefile/" + id);
    }

    public void deleteEpisodeFilesBulk(List<Integer> episodeFileIds) {
        if (episodeFileIds == null || episodeFileIds.isEmpty()) return;
        var payload = new java.util.HashMap<String, Object>();
        payload.put("episodeFileIds", episodeFileIds);
        delete("/api/v3/episodefile/bulk", payload);
    }

    public List<SonarrEpisode> getEpisodes(int seriesId, int seasonNumber) {
        return getList(
                "/api/v3/episode?seriesId=" + seriesId + "&seasonNumber=" + seasonNumber,
                new ParameterizedTypeReference<>() {});
    }

    public void setEpisodesMonitored(List<Integer> episodeIds, boolean monitored) {
        if (episodeIds == null || episodeIds.isEmpty()) return;
        var payload = new java.util.HashMap<String, Object>();
        payload.put("episodeIds", episodeIds);
        payload.put("monitored", monitored);
        put("/api/v3/episode/monitor", payload);
    }

    public List<SonarrQueueItem> getQueueForSeries(int seriesId) {
        List<SonarrQueueItem> all = new ArrayList<>();
        int page = 1;
        int totalRecords;
        do {
            SonarrQueuePage resp = get(
                    "/api/v3/queue?page=" + page
                            + "&pageSize=" + QUEUE_PAGE_SIZE
                            + "&seriesIds=" + seriesId
                            + "&includeEpisode=true",
                    SonarrQueuePage.class);
            if (resp == null || resp.records() == null) break;
            all.addAll(resp.records());
            totalRecords = resp.totalRecords();
            page++;
        } while (all.size() < totalRecords);
        return all;
    }

    public void deleteQueueItemsBulk(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        var payload = new java.util.HashMap<String, Object>();
        payload.put("ids", ids);
        delete("/api/v3/queue/bulk?removeFromClient=true&blocklist=false", payload);
    }

    public SonarrSeries getSeriesOrNull(int id) {
        return getOrNull("/api/v3/series/" + id, SonarrSeries.class);
    }

    public void deleteSeries(int id, boolean deleteFiles) {
        delete("/api/v3/series/" + id + "?deleteFiles=" + deleteFiles + "&addImportListExclusion=false");
    }

    // Unmonitors a single season via the dedicated seasonPass endpoint.
    // Only the specified season is touched; series-level monitored and other seasons are left untouched.
    public void unmonitorSeason(int seriesId, int seasonNumber) {
        var season = java.util.Map.of(
                "seasonNumber", seasonNumber,
                "monitored", false);
        var seriesEntry = java.util.Map.of(
                "id", seriesId,
                "seasons", List.of(season));
        var payload = java.util.Map.of(
                "series", List.of(seriesEntry));

        post("/api/v3/seasonpass", payload);
    }
}
