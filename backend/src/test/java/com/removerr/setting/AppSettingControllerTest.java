package com.removerr.setting;

import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.job.JobRunner;
import com.removerr.plexuser.UserSyncJob;
import com.removerr.setting.dto.UpdateSettingsRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppSettingControllerTest {

    private final AppSettingService settingService = mock(AppSettingService.class);
    private final JobRunner jobRunner = mock(JobRunner.class);
    private final AppSettingController controller =
            new AppSettingController(settingService, mock(PlexLibraryClient.class), jobRunner);
    private final UpdateSettingsRequest request =
            new UpdateSettingsRequest("http://plex:32400", null, null, null, null, null, null, null);

    @Test
    void updateSettings_syncsUsersWhenPlexUrlChanges() {
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn(null, "http://plex:32400");

        controller.updateSettings(request);

        verify(jobRunner).runInBackground(UserSyncJob.KEY, null);
    }

    @Test
    void updateSettings_doesNotSyncWhenPlexUrlIsUnchanged() {
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn("http://plex:32400");

        controller.updateSettings(request);

        verify(jobRunner, never()).runInBackground(anyString(), any());
    }

    @Test
    void updateSettings_succeedsWhenSyncIsAlreadyRunning() {
        when(settingService.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn(null, "http://plex:32400");
        doThrow(new IllegalStateException("running")).when(jobRunner).runInBackground(UserSyncJob.KEY, null);

        controller.updateSettings(request);

        verify(settingService).updateSettings(request);
    }
}
