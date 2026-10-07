package com.removerr.trash;

import com.removerr.audit.AuditLogService;
import com.removerr.integration.IntegrationException;
import com.removerr.integration.ResourceNotFoundException;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.seerr.SeerrClient;
import com.removerr.integration.sonarr.SonarrClient;
import com.removerr.integration.sonarr.dto.SonarrEpisode;
import com.removerr.integration.sonarr.dto.SonarrSeries;
import com.removerr.trash.dto.PurgeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurgeServiceTest {

    private static final int SERIES_ID = 7;
    private static final int SEASON = 2;

    private final TrashItemRepository trashRepo = mock(TrashItemRepository.class);
    private final SonarrClient sonarr = mock(SonarrClient.class);
    private final PurgeService purgeService = new PurgeService(trashRepo, mock(RadarrClient.class), sonarr,
            mock(SeerrClient.class), mock(AuditLogService.class));

    private final TrashItem season = new TrashItem("SEASON", "SONARR", SERIES_ID, null, 81189, SEASON,
            "Show", 2020, null, 0, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", null, null);

    @BeforeEach
    void setUp() {
        when(trashRepo.findDueToPurge(any())).thenReturn(List.of(season));
    }

    @Test
    void season_staysInTrashWhenSonarrIsUnreachable() {
        when(sonarr.getSeriesOrNull(SERIES_ID)).thenThrow(new IntegrationException("Service unreachable"));

        PurgeResult result = purgeService.purge(false, null);

        assertThat(result.failed()).isEqualTo(1);
        verify(trashRepo, never()).delete(season);
    }

    @Test
    void season_isDroppedWhenSeriesWasDeletedExternally() {
        when(sonarr.getSeriesOrNull(SERIES_ID)).thenReturn(null);

        PurgeResult result = purgeService.purge(false, null);

        assertThat(result.purged()).isEqualTo(1);
        verify(trashRepo).delete(season);
    }

    @Test
    void season_staysInTrashWhenAFileCannotBeDeleted() {
        givenSeasonWithFiles(101, 102);
        doThrow(new IntegrationException("bulk failed")).when(sonarr).deleteEpisodeFilesBulk(List.of(101, 102));
        doThrow(new IntegrationException("HTTP 500")).when(sonarr).deleteEpisodeFile(102);

        PurgeResult result = purgeService.purge(false, null);

        assertThat(result.failed()).isEqualTo(1);
        verify(trashRepo, never()).delete(season);
    }

    @Test
    void season_isPurgedWhenRemainingFilesAreAlreadyGone() {
        givenSeasonWithFiles(101, 102);
        doThrow(new IntegrationException("bulk failed")).when(sonarr).deleteEpisodeFilesBulk(List.of(101, 102));
        doThrow(new ResourceNotFoundException("HTTP 404")).when(sonarr).deleteEpisodeFile(102);

        PurgeResult result = purgeService.purge(false, null);

        assertThat(result.purged()).isEqualTo(1);
        verify(sonarr).deleteEpisodeFile(101);
        verify(trashRepo).delete(season);
    }

    private void givenSeasonWithFiles(int... fileIds) {
        when(sonarr.getSeriesOrNull(SERIES_ID)).thenReturn(mock(SonarrSeries.class));
        List<SonarrEpisode> episodes = IntStream.range(0, fileIds.length)
                .mapToObj(i -> new SonarrEpisode(i + 1, SERIES_ID, SEASON, i + 1, true, fileIds[i]))
                .toList();
        when(sonarr.getEpisodes(SERIES_ID, SEASON)).thenReturn(episodes);
    }
}
