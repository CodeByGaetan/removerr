package com.removerr.trash;

import com.removerr.audit.AuditLogService;
import com.removerr.integration.IntegrationException;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.integration.sonarr.dto.SonarrEpisodeFile;
import com.removerr.integration.sonarr.dto.SonarrSeason;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.setting.AppSettingService;
import com.removerr.trash.dto.TrashItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);

    private final TrashItemRepository trashRepo;
    private final RadarrClient radarrClient;
    private final SonarrClient sonarrClient;
    private final SeerrClient seerrClient;
    private final PlexUserRepository plexUserRepository;
    private final AuditLogService auditLogService;
    private final AppSettingService appSettingService;
    private final TransactionTemplate txTemplate;
    private final CacheManager cacheManager;

    public TrashService(TrashItemRepository trashRepo,
                        RadarrClient radarrClient,
                        SonarrClient sonarrClient,
                        SeerrClient seerrClient,
                        PlexUserRepository plexUserRepository,
                        AuditLogService auditLogService,
                        AppSettingService appSettingService,
                        PlatformTransactionManager txManager,
                        CacheManager cacheManager) {
        this.trashRepo = trashRepo;
        this.radarrClient = radarrClient;
        this.sonarrClient = sonarrClient;
        this.seerrClient = seerrClient;
        this.plexUserRepository = plexUserRepository;
        this.auditLogService = auditLogService;
        this.appSettingService = appSettingService;
        this.txTemplate = new TransactionTemplate(txManager);
        this.cacheManager = cacheManager;
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    public TrashItemResponse trashMovie(int radarrId, boolean immediate, Long userId) {
        RadarrMovie movie = radarrClient.getMovie(radarrId);
        String requestedBy = fetchSeerrRequestedByTmdb(movie.tmdbId());

        TrashItemResponse response = Objects.requireNonNull(txTemplate.execute(status -> {
            PlexUser user = plexUserRepository.getReferenceById(userId);
            TrashItem item = new TrashItem(
                    "MOVIE", "RADARR", radarrId,
                    movie.tmdbId(), null, null,
                    movie.title(), movie.year(), movie.posterUrl(),
                    movie.sizeOnDisk(),
                    nowStr(), purgeAtStr(immediate), user, requestedBy
            );
            return toResponse(trashRepo.save(item));
        }));

        auditLogService.log(userId, "TRASH", "MOVIE", String.valueOf(radarrId),
                AuditLogService.meta("title", movie.title(), "size", movie.sizeOnDisk()));
        evict("library");
        return response;
    }

    // ── Shows ─────────────────────────────────────────────────────────────────

    public TrashItemResponse trashShow(int sonarrId, boolean immediate, Long userId) {
        SonarrSeries series = sonarrClient.getSeries(sonarrId);
        // Sonarr reports sizeOnDisk=0 at the series level, so sum it from the episode files.
        long sizeBytes = series.sizeOnDisk() > 0
                ? series.sizeOnDisk()
                : sonarrClient.getEpisodeFiles(sonarrId).stream()
                        .mapToLong(SonarrEpisodeFile::size).sum();
        String requestedBy = fetchSeerrRequestedByTvdb(series.tvdbId());

        TrashItemResponse response = Objects.requireNonNull(txTemplate.execute(status -> {
            PlexUser user = plexUserRepository.getReferenceById(userId);
            TrashItem item = new TrashItem(
                    "SHOW", "SONARR", sonarrId,
                    null, series.tvdbId(), null,
                    series.title(), series.year(), series.posterUrl(),
                    sizeBytes,
                    nowStr(), purgeAtStr(immediate), user, requestedBy
            );
            return toResponse(trashRepo.save(item));
        }));

        auditLogService.log(userId, "TRASH", "SHOW", String.valueOf(sonarrId),
                AuditLogService.meta("title", series.title(), "size", sizeBytes));
        evict("library-shows");
        return response;
    }

    // ── Seasons ───────────────────────────────────────────────────────────────

    public TrashItemResponse trashSeason(int sonarrId, int seasonNumber, boolean immediate, Long userId) {
        SonarrSeries series = sonarrClient.getSeries(sonarrId);

        // If the only visible season is the one being trashed, treat it as a full series deletion.
        List<Integer> visibleSeasons = series.seasons() == null ? List.of() :
                series.seasons().stream()
                        .filter(SonarrSeason::isVisible)
                        .map(SonarrSeason::seasonNumber)
                        .toList();
        if (visibleSeasons.size() == 1 && visibleSeasons.get(0) == seasonNumber) {
            return trashShow(sonarrId, immediate, userId);
        }

        List<SonarrEpisodeFile> seasonFiles = sonarrClient.getEpisodeFiles(sonarrId).stream()
                .filter(f -> f.seasonNumber() == seasonNumber)
                .toList();
        long sizeBytes = seasonFiles.stream().mapToLong(SonarrEpisodeFile::size).sum();

        String title = series.title() + " — Saison " + seasonNumber;
        TrashItemResponse response = Objects.requireNonNull(txTemplate.execute(status -> {
            PlexUser user = plexUserRepository.getReferenceById(userId);
            TrashItem item = new TrashItem(
                    "SEASON", "SONARR", sonarrId,
                    null, series.tvdbId(), seasonNumber,
                    title, series.year(), series.posterUrl(),
                    sizeBytes,
                    nowStr(), purgeAtStr(immediate), user, null
            );
            return toResponse(trashRepo.save(item));
        }));

        auditLogService.log(userId, "TRASH", "SEASON", sonarrId + "-" + seasonNumber,
                AuditLogService.meta("title", title, "size", sizeBytes));
        evict("library-shows");
        return response;
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    public TrashItemResponse restore(Long trashItemId, Long userId) {
        TrashItemResponse[] holder = new TrashItemResponse[1];
        String[] titleHolder = new String[1];
        txTemplate.executeWithoutResult(status -> {
            TrashItem item = trashRepo.findById(trashItemId)
                    .orElseThrow(() -> new IllegalArgumentException("Trash item not found: " + trashItemId));
            titleHolder[0] = item.getTitle();
            holder[0] = toResponse(item);
            trashRepo.delete(item);
        });

        auditLogService.log(userId, "RESTORE", "TRASH_ITEM", String.valueOf(trashItemId),
                AuditLogService.meta("title", titleHolder[0]));
        evict("library");
        evict("library-shows");
        return holder[0];
    }

    public List<TrashItemResponse> getTrash() {
        return trashRepo.findAllByOrderByTrashedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void evict(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }

    private String fetchSeerrRequestedByTmdb(Integer tmdbId) {
        if (tmdbId == null) return null;
        try {
            return seerrClient.findByTmdbId(tmdbId)
                    .map(r -> r.requestedBy() != null ? r.requestedBy().displayName() : null)
                    .orElse(null);
        } catch (IntegrationException e) {
            log.debug("Seerr requestedBy lookup skipped for tmdbId {}: {}", tmdbId, e.getMessage());
            return null;
        }
    }

    private String fetchSeerrRequestedByTvdb(Integer tvdbId) {
        if (tvdbId == null) return null;
        try {
            return seerrClient.findByTvdbId(tvdbId)
                    .map(r -> r.requestedBy() != null ? r.requestedBy().displayName() : null)
                    .orElse(null);
        } catch (IntegrationException e) {
            log.debug("Seerr requestedBy lookup skipped for tvdbId {}: {}", tvdbId, e.getMessage());
            return null;
        }
    }

    private String nowStr() { return Instant.now().toString(); }

    private String purgeAtStr(boolean immediate) {
        if (immediate) return Instant.now().toString();
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(appSettingService.getRetentionDays())
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toString();
    }

    private TrashItemResponse toResponse(TrashItem item) {
        return new TrashItemResponse(
                item.getId(), item.getMediaType(), item.getExternalService(),
                item.getExternalId(), item.getTmdbId(), item.getTitle(),
                item.getYear(), item.getPosterUrl(),
                item.getSizeBytes(), item.getTrashedAt(), item.getPurgeAt(),
                item.getSeerrRequestedBy(),
                item.getTrashedBy().getId()
        );
    }
}
