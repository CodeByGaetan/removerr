package com.removerr.trash;

import com.removerr.audit.AuditLogService;
import com.removerr.integration.IntegrationException;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.integration.sonarr.dto.SonarrEpisode;
import com.removerr.integration.sonarr.dto.SonarrQueueItem;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.trash.dto.PurgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);

    private final TrashItemRepository trashRepo;
    private final RadarrClient radarrClient;
    private final SonarrClient sonarrClient;
    private final SeerrClient seerrClient;
    private final AuditLogService auditLogService;

    public PurgeService(TrashItemRepository trashRepo,
                        RadarrClient radarrClient,
                        SonarrClient sonarrClient,
                        SeerrClient seerrClient,
                        AuditLogService auditLogService) {
        this.trashRepo = trashRepo;
        this.radarrClient = radarrClient;
        this.sonarrClient = sonarrClient;
        this.seerrClient = seerrClient;
        this.auditLogService = auditLogService;
    }

    @Caching(evict = {
            @CacheEvict(value = "library", allEntries = true),
            @CacheEvict(value = "library-shows", allEntries = true)
    })
    public PurgeResult purge(boolean manual, Long userId) {
        String now = Instant.now().toString();
        List<TrashItem> candidates = trashRepo.findDueToPurge(now);

        if (candidates.isEmpty()) {
            log.info("Purge: nothing to purge");
            return new PurgeResult(0, 0);
        }

        log.info("Purge: {} item(s) eligible", candidates.size());

        int purged = 0;
        int failed = 0;

        for (TrashItem item : candidates) {
            try {
                purgeItem(item);
                trashRepo.delete(item);
                purged++;
                log.info("Purged: {} (id={})", item.getTitle(), item.getId());
            } catch (Exception e) {
                failed++;
                log.error("Failed to purge item {} ({}): {}", item.getId(), item.getTitle(), e.getMessage());
            }
        }

        log.info("Purge complete: {} purged, {} failed", purged, failed);

        if (purged > 0) {
            try {
                seerrClient.runAvailabilitySync();
                log.debug("Triggered Seerr availability-sync after purge");
            } catch (Exception e) {
                log.warn("Could not trigger Seerr availability-sync: {}", e.getMessage());
            }
        }

        String action = manual ? "PURGE_MANUAL" : "PURGE_AUTO";
        auditLogService.log(userId, action, null, null,
                AuditLogService.meta("purged", purged, "failed", failed));
        return new PurgeResult(purged, failed);
    }

    private void purgeItem(TrashItem item) {
        switch (item.getMediaType()) {
            case "MOVIE" -> purgeMovie(item);
            case "SHOW"  -> purgeShow(item);
            case "SEASON" -> purgeSeason(item);
            default -> log.warn("Unknown media type '{}' for trash item {}", item.getMediaType(), item.getId());
        }
    }

    private void purgeMovie(TrashItem item) {
        RadarrMovie movie = radarrClient.getMovieOrNull(item.getExternalId());
        if (movie == null) {
            log.info("Movie {} not found in Radarr — already deleted externally", item.getExternalId());
        } else {
            radarrClient.deleteMovie(item.getExternalId(), true);
            log.debug("Deleted movie {} from Radarr (deleteFiles=true)", item.getExternalId());
        }
        deleteSeerrByTmdb(item.getTmdbId());
    }

    private void purgeShow(TrashItem item) {
        SonarrSeries series = sonarrClient.getSeriesOrNull(item.getExternalId());
        if (series == null) {
            log.info("Show {} not found in Sonarr — already deleted externally", item.getExternalId());
        } else {
            sonarrClient.deleteSeries(item.getExternalId(), true);
            log.debug("Deleted series {} from Sonarr (deleteFiles=true)", item.getExternalId());
        }
        deleteSeerrByTvdb(item.getTvdbId());
    }

    private void purgeSeason(TrashItem item) {
        Integer seasonNumber = item.getSeasonNumber();
        if (seasonNumber == null) {
            log.warn("Season trash item {} has no seasonNumber — skipped", item.getId());
            return;
        }
        int seriesId = item.getExternalId();

        try {
            List<SonarrEpisode> episodes = sonarrClient.getEpisodes(seriesId, seasonNumber);
            List<Integer> episodeIds = episodes.stream().map(SonarrEpisode::id).toList();

            // 1. Cancel in-flight downloads for these episodes
            try {
                List<Integer> queueItemIds = sonarrClient.getQueueForSeries(seriesId).stream()
                        .filter(q -> q.episodeId() != null && episodeIds.contains(q.episodeId()))
                        .map(SonarrQueueItem::id)
                        .toList();
                if (!queueItemIds.isEmpty()) {
                    try {
                        sonarrClient.deleteQueueItemsBulk(queueItemIds);
                        log.debug("Cancelled {} queue item(s) for season {}-{}", queueItemIds.size(), seriesId, seasonNumber);
                    } catch (IntegrationException e) {
                        log.warn("Could not cancel queue items for season {}-{}: {}", seriesId, seasonNumber, e.getMessage());
                    }
                }
            } catch (IntegrationException e) {
                log.warn("Could not read Sonarr queue for series {}: {}", seriesId, e.getMessage());
            }

            // 2. Unmonitor every episode in the season (stops future searches at episode level)
            if (!episodeIds.isEmpty()) {
                try {
                    sonarrClient.setEpisodesMonitored(episodeIds, false);
                } catch (IntegrationException e) {
                    log.warn("Could not unmonitor episodes of season {}-{}: {}", seriesId, seasonNumber, e.getMessage());
                }
            }

            // 3. Bulk-delete every downloaded file of the season
            List<Integer> episodeFileIds = episodes.stream()
                    .map(SonarrEpisode::episodeFileId)
                    .filter(id -> id != null && id > 0)
                    .toList();
            if (!episodeFileIds.isEmpty()) {
                try {
                    sonarrClient.deleteEpisodeFilesBulk(episodeFileIds);
                } catch (IntegrationException e) {
                    log.warn("Bulk delete failed for season {}-{}, falling back to per-file: {}", seriesId, seasonNumber, e.getMessage());
                    for (Integer fileId : episodeFileIds) {
                        try {
                            sonarrClient.deleteEpisodeFile(fileId);
                        } catch (IntegrationException e2) {
                            log.warn("Could not delete episode file {} from Sonarr: {}", fileId, e2.getMessage());
                        }
                    }
                }
            }

            // 4. Unmonitor the season itself
            try {
                sonarrClient.unmonitorSeason(seriesId, seasonNumber);
            } catch (IntegrationException e) {
                log.warn("Could not unmonitor season {}-{}: {}", seriesId, seasonNumber, e.getMessage());
            }

            log.debug("Purged season {}-{} from Sonarr ({} episodes, {} files)",
                    seriesId, seasonNumber, episodeIds.size(), episodeFileIds.size());
        } catch (IntegrationException e) {
            log.info("Season {}-{} not found in Sonarr — already deleted externally",
                    seriesId, seasonNumber);
        }

        // 5. Drop Seerr request(s) covering this season
        deleteSeerrSeasonRequests(item.getTvdbId(), seasonNumber);
    }

    private void deleteSeerrSeasonRequests(Integer tvdbId, int seasonNumber) {
        if (tvdbId == null) return;
        try {
            seerrClient.findRequestsByTvdbAndSeason(tvdbId, seasonNumber)
                    .forEach(r -> seerrClient.deleteRequest(r.id()));
        } catch (IntegrationException e) {
            log.warn("Seerr cleanup skipped for tvdbId {} season {}: {}", tvdbId, seasonNumber, e.getMessage());
        }
    }

    private void deleteSeerrByTmdb(Integer tmdbId) {
        if (tmdbId == null) return;
        try {
            seerrClient.findByTmdbId(tmdbId)
                    .ifPresent(r -> seerrClient.deleteRequest(r.id()));
        } catch (IntegrationException e) {
            log.warn("Seerr cleanup skipped for tmdbId {}: {}", tmdbId, e.getMessage());
        }
    }

    private void deleteSeerrByTvdb(Integer tvdbId) {
        if (tvdbId == null) return;
        try {
            seerrClient.findByTvdbId(tvdbId)
                    .ifPresent(r -> seerrClient.deleteRequest(r.id()));
        } catch (IntegrationException e) {
            log.warn("Seerr cleanup skipped for tvdbId {}: {}", tvdbId, e.getMessage());
        }
    }
}
