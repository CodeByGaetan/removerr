package com.removerr.integration.seerr;

import com.removerr.integration.ArrBaseClient;
import com.removerr.integration.seerr.dto.SeerrPagedResponse;
import com.removerr.integration.seerr.dto.SeerrRequest;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SeerrClient extends ArrBaseClient {

    private static final int PAGE_SIZE = 100;

    // Self proxy: required so the find methods go through the AOP proxy when calling
    // getAllRequests(), otherwise @Cacheable is bypassed by self-invocation.
    private final SeerrClient self;

    public SeerrClient(AppSettingService settings, @Lazy SeerrClient self) {
        super(settings, AppSettingKey.SEERR_URL, AppSettingKey.SEERR_API_KEY);
        this.self = self;
    }

    @Cacheable("seerr-requests")
    public List<SeerrRequest> getAllRequests() {
        List<SeerrRequest> all = new ArrayList<>();
        int page = 1;
        int totalPages;

        do {
            SeerrPagedResponse resp = get(
                    "/api/v1/request?take=" + PAGE_SIZE + "&skip=" + ((page - 1) * PAGE_SIZE),
                    SeerrPagedResponse.class);
            if (resp == null || resp.results() == null) break;
            all.addAll(resp.results());
            totalPages = resp.pageInfo() != null ? resp.pageInfo().pages() : 1;
            page++;
        } while (page <= totalPages);

        return all;
    }

    // TMDB numbers movies and TV shows separately: the same id can be both a movie and a show,
    // so the request type must be checked too. A media can have several requests (e.g. a 4K one).
    public List<SeerrRequest> findMovieRequestsByTmdbId(int tmdbId) {
        return self.getAllRequests().stream()
                .filter(r -> SeerrRequest.MOVIE.equals(r.type()) && r.media() != null
                        && Integer.valueOf(tmdbId).equals(r.media().tmdbId()))
                .toList();
    }

    public List<SeerrRequest> findShowRequestsByTvdbId(int tvdbId) {
        return self.getAllRequests().stream()
                .filter(r -> SeerrRequest.TV.equals(r.type()) && r.media() != null
                        && Integer.valueOf(tvdbId).equals(r.media().tvdbId()))
                .toList();
    }

    public List<SeerrRequest> findRequestsByTvdbAndSeason(int tvdbId, int seasonNumber) {
        return self.getAllRequests().stream()
                .filter(r -> SeerrRequest.TV.equals(r.type()) && r.media() != null
                        && Integer.valueOf(tvdbId).equals(r.media().tvdbId())
                        && r.seasons() != null
                        && r.seasons().stream().anyMatch(s -> s.seasonNumber() == seasonNumber))
                .toList();
    }

    @CacheEvict(value = "seerr-requests", allEntries = true)
    public void deleteRequest(int id) {
        delete("/api/v1/request/" + id);
    }

    // Triggers Seerr's availability-sync job: re-checks each AVAILABLE/PARTIALLY_AVAILABLE
    // media against Sonarr/Radarr/Plex and downgrades the status of seasons whose files are gone.
    // Required after a purge so the freed seasons become requestable again.
    public void runAvailabilitySync() {
        post("/api/v1/settings/jobs/availability-sync/run", java.util.Map.of());
    }
}
