package com.removerr.library;

import com.removerr.integration.IntegrationException;
import com.removerr.integration.ServiceNotConfiguredException;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.integration.plex.dto.PlexHistoryEntry;
import com.removerr.integration.plex.dto.PlexMetadata;
import com.removerr.integration.plex.dto.PlexSection;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.integration.sonarr.dto.SonarrSeason;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.library.dto.MediaCard;
import com.removerr.library.dto.SeerrInfo;
import com.removerr.library.dto.SeasonCard;
import com.removerr.library.dto.ShowCard;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.trash.TrashItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Service
public class MediaAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MediaAggregationService.class);

    private final RadarrClient radarrClient;
    private final SonarrClient sonarrClient;
    private final SeerrClient seerrClient;
    private final PlexLibraryClient plexClient;
    private final TrashItemRepository trashRepo;
    private final PlexUserRepository plexUserRepository;

    public MediaAggregationService(RadarrClient radarrClient, SonarrClient sonarrClient,
                                   SeerrClient seerrClient, PlexLibraryClient plexClient,
                                   TrashItemRepository trashRepo, PlexUserRepository plexUserRepository) {
        this.radarrClient = radarrClient;
        this.sonarrClient = sonarrClient;
        this.seerrClient = seerrClient;
        this.plexClient = plexClient;
        this.trashRepo = trashRepo;
        this.plexUserRepository = plexUserRepository;
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    @Cacheable("library")
    public List<MediaCard> getMovies() {
        Set<Integer> trashedMovieIds = trashRepo.findTrashedExternalIds("MOVIE");
        Set<Integer> countedUserIds = plexUserRepository.findByCountedTrue()
                .stream().filter(u -> u.getPlexAccountId() != null)
                .map(u -> u.getPlexAccountId()).collect(Collectors.toSet());

        CompletableFuture<List<RadarrMovie>> moviesFuture =
                CompletableFuture.supplyAsync(radarrClient::getMovies);
        CompletableFuture<Map<Integer, SeerrInfo>> seerrFuture =
                CompletableFuture.supplyAsync(this::fetchSeerrMovieMap);
        CompletableFuture<Map<Integer, PlexMetadata>> plexFuture =
                CompletableFuture.supplyAsync(this::fetchPlexMovieMap);
        CompletableFuture<List<PlexHistoryEntry>> historyFuture =
                CompletableFuture.supplyAsync(this::fetchPlexHistory);

        List<RadarrMovie> movies = joinUnwrapped(moviesFuture);
        Map<Integer, SeerrInfo> seerrMap = joinUnwrapped(seerrFuture);
        Map<Integer, PlexMetadata> plexMap = joinUnwrapped(plexFuture);
        List<PlexHistoryEntry> history = joinUnwrapped(historyFuture);

        Map<String, Set<Integer>> movieViewersByRatingKey = buildMovieViewerMap(history);

        return movies.stream()
                .filter(m -> !trashedMovieIds.contains(m.id()))
                .map(m -> {
                    PlexMetadata plex = plexMap != null ? plexMap.get(m.tmdbId()) : null;
                    Integer plexUniqueViewers = computeUniqueViewers(
                            plex != null ? plex.ratingKey() : null,
                            movieViewersByRatingKey, countedUserIds);
                    return new MediaCard(
                            "movie",
                            m.id(),
                            m.tmdbId(),
                            m.title(),
                            m.year(),
                            m.posterUrl(),
                            m.sizeOnDisk(),
                            m.hasFile(),
                            m.monitored(),
                            m.added(),
                            seerrMap.get(m.tmdbId()),
                            plex != null ? plex.viewCount() : (plexMap == null ? null : 0),
                            plexUniqueViewers
                    );
                })
                .toList();
    }

    // ── Shows ─────────────────────────────────────────────────────────────────

    @Cacheable("library-shows")
    public List<ShowCard> getSeries() {
        Set<Integer> trashedShowIds = trashRepo.findTrashedExternalIds("SHOW");
        Set<String> trashedSeasonKeys = trashRepo.findTrashedSeasons().stream()
                .map(t -> t.getExternalId() + ":" + t.getSeasonNumber())
                .collect(Collectors.toSet());
        Set<Integer> countedUserIds = plexUserRepository.findByCountedTrue()
                .stream().filter(u -> u.getPlexAccountId() != null)
                .map(u -> u.getPlexAccountId()).collect(Collectors.toSet());

        CompletableFuture<List<SonarrSeries>> seriesFuture =
                CompletableFuture.supplyAsync(sonarrClient::getSeries);
        CompletableFuture<Map<Integer, SeerrInfo>> seerrFuture =
                CompletableFuture.supplyAsync(this::fetchSeerrShowMap);
        CompletableFuture<Map<Integer, PlexMetadata>> plexFuture =
                CompletableFuture.supplyAsync(this::fetchPlexShowMap);
        CompletableFuture<List<PlexHistoryEntry>> historyFuture =
                CompletableFuture.supplyAsync(this::fetchPlexHistory);

        List<SonarrSeries> series = joinUnwrapped(seriesFuture);
        Map<Integer, SeerrInfo> seerrMap = joinUnwrapped(seerrFuture);
        Map<Integer, PlexMetadata> plexMap = joinUnwrapped(plexFuture);
        List<PlexHistoryEntry> history = joinUnwrapped(historyFuture);

        Map<String, Map<Integer, Map<Integer, Set<Integer>>>> episodeViewersByShow = buildEpisodeViewerMap(history);

        return series.stream()
                .filter(s -> !trashedShowIds.contains(s.id()))
                .map(s -> {
                    PlexMetadata plexShow = plexMap != null ? plexMap.get(s.tvdbId()) : null;
                    String showRatingKey = plexShow != null ? plexShow.ratingKey() : null;

                    List<SonarrSeason> visibleSeasons = s.seasons() == null ? List.of() :
                            s.seasons().stream()
                                    .filter(SonarrSeason::isVisible)
                                    .filter(season -> !trashedSeasonKeys.contains(s.id() + ":" + season.seasonNumber()))
                                    .toList();

                    List<SeasonCard> seasons = visibleSeasons.stream()
                            .map(season -> {
                                int totalEpisodes = season.statistics() != null
                                        ? season.statistics().totalEpisodeCount() : 0;
                                Integer seasonViewers = computeSeasonUniqueViewers(
                                        showRatingKey, episodeViewersByShow,
                                        season.seasonNumber(), totalEpisodes, countedUserIds);
                                return new SeasonCard(
                                        season.seasonNumber(),
                                        season.statistics() != null ? season.statistics().episodeFileCount() : 0,
                                        totalEpisodes,
                                        season.statistics() != null ? season.statistics().sizeOnDisk() : 0,
                                        season.monitored(),
                                        seasonViewers
                                );
                            })
                            .toList();

                    // Show is "watched" by a user only if they watched the last episode of the last
                    // visible season (Sonarr-authoritative, even if not downloaded yet).
                    SonarrSeason lastSeason = visibleSeasons.stream()
                            .max(Comparator.comparingInt(SonarrSeason::seasonNumber))
                            .orElse(null);
                    Integer showUniqueViewers = computeSeasonUniqueViewers(
                            showRatingKey, episodeViewersByShow,
                            lastSeason != null ? lastSeason.seasonNumber() : 0,
                            lastSeason != null && lastSeason.statistics() != null
                                    ? lastSeason.statistics().totalEpisodeCount() : 0,
                            countedUserIds);

                    return new ShowCard(
                            "show",
                            s.id(),
                            s.tvdbId(),
                            s.title(),
                            s.year(),
                            s.posterUrl(),
                            s.sizeOnDisk(),
                            s.monitored(),
                            s.added(),
                            seasons,
                            seerrMap.get(s.tvdbId()),
                            plexShow != null ? plexShow.viewedLeafCount() : null,
                            plexShow != null ? plexShow.leafCount() : null,
                            showUniqueViewers
                    );
                })
                .toList();
    }

    // CompletableFuture.join() wraps any exception in CompletionException, which
    // would hide the original ServiceNotConfiguredException / IntegrationException
    // from the global handler. Unwrap so the proper HTTP status is returned.
    private static <T> T joinUnwrapped(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    // ── Private helpers — viewer computation ─────────────────────────────────

    private Integer computeUniqueViewers(String ratingKey, Map<String, Set<Integer>> viewerMap,
                                         Set<Integer> countedIds) {
        if (viewerMap == null) return null;
        if (ratingKey == null) return 0;
        return (int) viewerMap.getOrDefault(ratingKey, Set.of())
                .stream().filter(countedIds::contains).count();
    }

    private Map<String, Set<Integer>> buildMovieViewerMap(List<PlexHistoryEntry> history) {
        if (history == null) return null;
        return history.stream()
                .filter(e -> "movie".equals(e.type()) && e.effectiveRatingKey() != null)
                .collect(Collectors.groupingBy(
                        PlexHistoryEntry::effectiveRatingKey,
                        Collectors.mapping(PlexHistoryEntry::accountId, Collectors.toSet())
                ));
    }

    // Indexed by: showRatingKey → seasonNumber → episodeNumber → set of accountIds who watched it.
    // Returns null if history is null (Plex not configured).
    private Map<String, Map<Integer, Map<Integer, Set<Integer>>>> buildEpisodeViewerMap(List<PlexHistoryEntry> history) {
        if (history == null) return null;
        return history.stream()
                .filter(e -> "episode".equals(e.type())
                        && e.grandparentRatingKey() != null
                        && e.parentIndex() != null
                        && e.index() != null)
                .collect(Collectors.groupingBy(
                        PlexHistoryEntry::grandparentRatingKey,
                        Collectors.groupingBy(
                                PlexHistoryEntry::parentIndex,
                                Collectors.groupingBy(
                                        PlexHistoryEntry::index,
                                        Collectors.mapping(PlexHistoryEntry::accountId, Collectors.toSet())
                                )
                        )
                ));
    }

    // A user is considered to have watched the season only if they scrobbled the LAST episode
    // (= episode number == totalEpisodes from Sonarr, even if not downloaded locally).
    private Integer computeSeasonUniqueViewers(String showRatingKey,
                                               Map<String, Map<Integer, Map<Integer, Set<Integer>>>> episodeViewersByShow,
                                               int seasonNumber, int totalEpisodes, Set<Integer> countedIds) {
        if (episodeViewersByShow == null) return null;
        if (showRatingKey == null || totalEpisodes <= 0) return 0;
        Map<Integer, Map<Integer, Set<Integer>>> seasonMap = episodeViewersByShow.get(showRatingKey);
        if (seasonMap == null) return 0;
        Map<Integer, Set<Integer>> episodeMap = seasonMap.get(seasonNumber);
        if (episodeMap == null) return 0;
        return (int) episodeMap.getOrDefault(totalEpisodes, Set.of())
                .stream().filter(countedIds::contains).count();
    }

    // ── Private helpers — fetch ───────────────────────────────────────────────

    private static final int PLEX_HISTORY_LIMIT = 10_000;

    private List<PlexHistoryEntry> fetchPlexHistory() {
        try {
            List<PlexHistoryEntry> history = plexClient.getViewHistory(PLEX_HISTORY_LIMIT);
            if (history.size() >= PLEX_HISTORY_LIMIT) {
                log.warn("Plex view history hit the {} entries limit — viewer stats may be truncated",
                        PLEX_HISTORY_LIMIT);
            }
            return history;
        } catch (ServiceNotConfiguredException e) {
            log.info("Plex not configured — skipping view history");
            return null;
        } catch (IntegrationException e) {
            log.warn("Plex history fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<Integer, SeerrInfo> fetchSeerrMovieMap() {
        try {
            return seerrClient.getAllRequests().stream()
                    .filter(r -> "movie".equals(r.type()) && r.media() != null && r.requestedBy() != null)
                    .collect(Collectors.toMap(
                            r -> r.media().tmdbId(),
                            r -> new SeerrInfo(r.id(), r.requestedBy().displayName(),
                                    r.requestedBy().avatar(), r.createdAt()),
                            (existing, dup) -> existing
                    ));
        } catch (ServiceNotConfiguredException e) {
            log.info("Seerr not configured — skipping movie request enrichment");
            return Map.of();
        }
    }

    private Map<Integer, PlexMetadata> fetchPlexMovieMap() {
        try {
            List<PlexSection> movieSections = plexClient.getSections().stream()
                    .filter(s -> "movie".equals(s.type()))
                    .toList();
            return movieSections.stream()
                    .flatMap(section -> plexClient.getMediaItems(section.key()).stream())
                    .filter(item -> item.tmdbId() != null)
                    .collect(Collectors.toMap(
                            PlexMetadata::tmdbId,
                            item -> item,
                            (a, b) -> a
                    ));
        } catch (ServiceNotConfiguredException e) {
            log.info("Plex not configured — skipping movie watch status enrichment");
            return null;
        } catch (IntegrationException e) {
            log.warn("Plex call failed — skipping movie watch status enrichment: {}", e.getMessage());
            return null;
        }
    }

    private Map<Integer, SeerrInfo> fetchSeerrShowMap() {
        try {
            return seerrClient.getAllRequests().stream()
                    .filter(r -> "tv".equals(r.type()) && r.media() != null
                              && r.media().tvdbId() != null && r.requestedBy() != null)
                    .collect(Collectors.toMap(
                            r -> r.media().tvdbId(),
                            r -> new SeerrInfo(r.id(), r.requestedBy().displayName(),
                                    r.requestedBy().avatar(), r.createdAt()),
                            (existing, dup) -> existing
                    ));
        } catch (ServiceNotConfiguredException e) {
            log.info("Seerr not configured — skipping show request enrichment");
            return Map.of();
        }
    }

    private Map<Integer, PlexMetadata> fetchPlexShowMap() {
        try {
            List<PlexSection> showSections = plexClient.getSections().stream()
                    .filter(s -> "show".equals(s.type()))
                    .toList();
            return showSections.stream()
                    .flatMap(section -> plexClient.getMediaItems(section.key()).stream())
                    .filter(item -> item.tvdbId() != null)
                    .collect(Collectors.toMap(
                            PlexMetadata::tvdbId,
                            item -> item,
                            (a, b) -> a
                    ));
        } catch (ServiceNotConfiguredException e) {
            log.info("Plex not configured — skipping show watch status enrichment");
            return null;
        } catch (IntegrationException e) {
            log.warn("Plex call failed — skipping show watch status enrichment: {}", e.getMessage());
            return null;
        }
    }
}
