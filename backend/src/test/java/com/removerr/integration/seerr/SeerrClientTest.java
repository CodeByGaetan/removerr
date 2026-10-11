package com.removerr.integration.seerr;

import com.removerr.integration.seerr.dto.SeerrMedia;
import com.removerr.integration.seerr.dto.SeerrRequest;
import com.removerr.setting.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeerrClientTest {

    private static final int SHARED_TMDB_ID = 1399;

    private final SeerrClient self = mock(SeerrClient.class);
    private final SeerrClient client = new SeerrClient(mock(AppSettingService.class), self);

    private final SeerrRequest showRequest = request(1, SeerrRequest.TV, SHARED_TMDB_ID, 121361);
    private final SeerrRequest movieRequest = request(2, SeerrRequest.MOVIE, SHARED_TMDB_ID, null);

    @BeforeEach
    void setUp() {
        // The show request comes first: a lookup ignoring the type would return it.
        when(self.getAllRequests()).thenReturn(List.of(showRequest, movieRequest));
    }

    @Test
    void findMovieRequestsByTmdbId_ignoresShowWithSameTmdbId() {
        assertThat(client.findMovieRequestsByTmdbId(SHARED_TMDB_ID)).containsExactly(movieRequest);
    }

    @Test
    void findMovieRequestsByTmdbId_returnsEveryRequestOfTheMovie() {
        SeerrRequest fourK = request(4, SeerrRequest.MOVIE, SHARED_TMDB_ID, null);
        when(self.getAllRequests()).thenReturn(List.of(movieRequest, showRequest, fourK));

        assertThat(client.findMovieRequestsByTmdbId(SHARED_TMDB_ID)).containsExactly(movieRequest, fourK);
    }

    @Test
    void findShowRequestsByTvdbId_onlyMatchesShows() {
        assertThat(client.findShowRequestsByTvdbId(121361)).containsExactly(showRequest);
    }

    @Test
    void findMovieRequestsByTmdbId_toleratesRequestsWithoutTmdbId() {
        SeerrRequest withoutTmdbId = request(3, SeerrRequest.MOVIE, null, null);
        when(self.getAllRequests()).thenReturn(List.of(withoutTmdbId, movieRequest));

        assertThat(client.findMovieRequestsByTmdbId(SHARED_TMDB_ID)).containsExactly(movieRequest);
    }

    private static SeerrRequest request(int id, String type, Integer tmdbId, Integer tvdbId) {
        return new SeerrRequest(id, 0, type, new SeerrMedia(type, tmdbId, tvdbId), null, List.of(), null);
    }
}
