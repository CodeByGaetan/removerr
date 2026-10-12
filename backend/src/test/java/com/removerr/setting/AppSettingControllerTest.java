package com.removerr.setting;

import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.job.JobRunner;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.UserSyncJob;
import com.removerr.setting.dto.UpdateSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppSettingControllerTest {

    private static final long OWNER_ID = 1L;
    private static final long ADMIN_ID = 2L;
    private static final String SAVED_PLEX = "http://plex:32400";
    private static final String SAVED_RADARR = "http://radarr:7878";
    // Nothing listens on port 1: tests fail fast without leaving the machine.
    private static final String ATTACKER = "http://localhost:1";

    private final AppSettingService settingService = mock(AppSettingService.class);
    private final JobRunner jobRunner = mock(JobRunner.class);
    private final PlexLibraryClient plexClient = mock(PlexLibraryClient.class);
    private final PlexUserRepository users = mock(PlexUserRepository.class);
    private final AppSettingController controller =
            new AppSettingController(settingService, plexClient, jobRunner, users);

    @BeforeEach
    void setUp() {
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(new PlexUser(9L, "owner", null, null, null, null)));
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(new PlexUser(2, "admin")));
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn(SAVED_PLEX);
        when(settingService.get(AppSettingKey.RADARR_URL)).thenReturn(SAVED_RADARR);
        when(settingService.get(AppSettingKey.RADARR_API_KEY)).thenReturn("saved-key");
        when(plexClient.findOwnerToken()).thenReturn(Optional.of("owner-token"));
    }

    @Test
    void updateSettings_syncsUsersWhenPlexUrlChanges() {
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn(SAVED_PLEX, "http://new-plex:32400");

        controller.updateSettings(plexUrl("http://new-plex:32400"), OWNER_ID);

        verify(jobRunner).runInBackground(UserSyncJob.KEY, null);
    }

    @Test
    void updateSettings_doesNotSyncWhenPlexUrlIsUnchanged() {
        controller.updateSettings(plexUrl(SAVED_PLEX), OWNER_ID);

        verify(jobRunner, never()).runInBackground(anyString(), any());
    }

    @Test
    void updateSettings_succeedsWhenSyncIsAlreadyRunning() {
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn(SAVED_PLEX, "http://new-plex:32400");
        doThrow(new IllegalStateException("running")).when(jobRunner).runInBackground(UserSyncJob.KEY, null);

        controller.updateSettings(plexUrl("http://new-plex:32400"), OWNER_ID);

        verify(settingService).updateSettings(any());
    }

    @Test
    void updateSettings_onlyTheOwnerCanChangeThePlexUrl() {
        assertThatThrownBy(() -> controller.updateSettings(plexUrl(ATTACKER), ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("owner");
        verify(settingService, never()).updateSettings(any());
    }

    @Test
    void updateSettings_newArrUrlRequiresTheKeyForNonOwners() {
        assertThatThrownBy(() -> controller.updateSettings(radarr(ATTACKER, null), ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class);

        controller.updateSettings(radarr(ATTACKER, "new-key"), ADMIN_ID);
        controller.updateSettings(radarr(SAVED_RADARR + "/", null), ADMIN_ID);
        verify(settingService, times(2)).updateSettings(any());
    }

    @Test
    void testConnections_neverSendsSavedSecretsToAnotherUrlForNonOwners() {
        controller.testConnections(new UpdateSettingsRequest(ATTACKER, ATTACKER, null, null, null, null, null, null), ADMIN_ID);

        verify(plexClient, never()).findOwnerToken();
        verify(settingService, never()).get(AppSettingKey.RADARR_API_KEY);
    }

    @Test
    void testConnections_usesSavedSecretsForTheSavedUrlsOrForTheOwner() {
        controller.testConnections(new UpdateSettingsRequest(null, null, null, null, null, null, null, null), ADMIN_ID);
        controller.testConnections(new UpdateSettingsRequest(ATTACKER, ATTACKER, null, null, null, null, null, null), OWNER_ID);

        verify(plexClient, times(2)).findOwnerToken();
        verify(settingService, times(2)).get(AppSettingKey.RADARR_API_KEY);
    }

    private static UpdateSettingsRequest plexUrl(String url) {
        return new UpdateSettingsRequest(url, null, null, null, null, null, null, null);
    }

    private static UpdateSettingsRequest radarr(String url, String key) {
        return new UpdateSettingsRequest(null, url, key, null, null, null, null, null);
    }
}
