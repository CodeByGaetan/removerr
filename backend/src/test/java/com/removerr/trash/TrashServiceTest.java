package com.removerr.trash;

import com.removerr.audit.AuditLogService;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.setting.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrashServiceTest {

    private static final long USER_ID = 1L;
    private static final int RADARR_ID = 10;
    private static final int SONARR_ID = 20;

    private final TrashItemRepository trashRepo = mock(TrashItemRepository.class);
    private final RadarrClient radarr = mock(RadarrClient.class);
    private final SonarrClient sonarr = mock(SonarrClient.class);
    private final PlexUserRepository plexUsers = mock(PlexUserRepository.class);
    private final TrashService service = new TrashService(trashRepo, radarr, sonarr, mock(SeerrClient.class),
            plexUsers, mock(AuditLogService.class), mock(AppSettingService.class),
            mock(PlatformTransactionManager.class), new ConcurrentMapCacheManager());

    @BeforeEach
    void setUp() {
        PlexUser user = mock(PlexUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(plexUsers.getReferenceById(USER_ID)).thenReturn(user);
        // A persisted item has an id; the response built from it needs one.
        when(trashRepo.save(any())).thenAnswer(inv -> {
            TrashItem persisted = spy((TrashItem) inv.getArgument(0));
            doReturn(99L).when(persisted).getId();
            return persisted;
        });

        RadarrMovie movie = mock(RadarrMovie.class);
        when(movie.tmdbId()).thenReturn(27205);
        when(radarr.getMovie(RADARR_ID)).thenReturn(movie);

        SonarrSeries series = mock(SonarrSeries.class);
        when(series.seasons()).thenReturn(List.of());
        when(sonarr.getSeries(SONARR_ID)).thenReturn(series);
    }

    @Test
    void trashMovie_rejectsMovieAlreadyInTrash() {
        when(trashRepo.existsByMediaTypeAndExternalId("MOVIE", RADARR_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.trashMovie(RADARR_ID, false, USER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(trashRepo, never()).save(any());
    }

    @Test
    void trashShow_rejectsShowAlreadyInTrash() {
        when(trashRepo.existsByMediaTypeAndExternalId("SHOW", SONARR_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.trashShow(SONARR_ID, false, USER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(trashRepo, never()).save(any());
    }

    @Test
    void trashShow_replacesSeasonsAlreadyInTrash() {
        service.trashShow(SONARR_ID, false, USER_ID);

        verify(trashRepo).deleteByMediaTypeAndExternalId("SEASON", SONARR_ID);
        verify(trashRepo).save(any());
    }

    @Test
    void trashSeason_rejectsSeasonWhoseShowIsAlreadyInTrash() {
        when(trashRepo.existsByMediaTypeAndExternalId("SHOW", SONARR_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.trashSeason(SONARR_ID, 2, false, USER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(trashRepo, never()).save(any());
    }

    @Test
    void trashSeason_rejectsSeasonAlreadyInTrash() {
        when(trashRepo.existsByMediaTypeAndExternalIdAndSeasonNumber("SEASON", SONARR_ID, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.trashSeason(SONARR_ID, 2, false, USER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(trashRepo, never()).save(any());
    }
}
