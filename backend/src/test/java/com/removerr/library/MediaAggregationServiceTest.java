package com.removerr.library;

import com.removerr.integration.IntegrationException;
import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.library.dto.MediaCard;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.trash.TrashItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaAggregationServiceTest {

    private final RadarrClient radarr = mock(RadarrClient.class);
    private final SeerrClient seerr = mock(SeerrClient.class);
    private final PlexLibraryClient plex = mock(PlexLibraryClient.class);
    private final MediaAggregationService service = new MediaAggregationService(radarr, mock(SonarrClient.class),
            seerr, plex, mock(TrashItemRepository.class), mock(PlexUserRepository.class));

    @Test
    void getMovies_stillListsMoviesWhenSeerrAndPlexAreDown() {
        RadarrMovie movie = mock(RadarrMovie.class);
        when(movie.id()).thenReturn(1);
        when(movie.tmdbId()).thenReturn(27205);
        when(radarr.getMovies()).thenReturn(List.of(movie));
        when(seerr.getAllRequests()).thenThrow(new IntegrationException("Seerr unreachable"));
        when(plex.getSections()).thenThrow(new IntegrationException("Plex unreachable"));
        when(plex.getViewHistory(10_000)).thenThrow(new IntegrationException("Plex unreachable"));

        List<MediaCard> movies = service.getMovies();

        assertThat(movies).hasSize(1);
        assertThat(movies.getFirst().seerr()).isNull();
    }
}
