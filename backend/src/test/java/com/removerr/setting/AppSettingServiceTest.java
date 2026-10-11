package com.removerr.setting;

import com.removerr.crypto.AesGcmEncryptor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppSettingServiceTest {

    private final AppSettingRepository repository = mock(AppSettingRepository.class);
    private final AppSettingService service = new AppSettingService(repository, mock(AesGcmEncryptor.class));

    @Test
    void get_readsTheDatabaseOnlyOnce() {
        when(repository.findById(AppSettingKey.RADARR_URL))
                .thenReturn(Optional.of(new AppSetting(AppSettingKey.RADARR_URL, "http://radarr:7878", "now")));

        service.get(AppSettingKey.RADARR_URL);
        String url = service.get(AppSettingKey.RADARR_URL);

        assertThat(url).isEqualTo("http://radarr:7878");
        verify(repository, times(1)).findById(AppSettingKey.RADARR_URL);
    }

    @Test
    void set_updatesTheValueReadAfterwards() {
        when(repository.findById(AppSettingKey.RADARR_URL)).thenReturn(Optional.empty());
        service.get(AppSettingKey.RADARR_URL);

        service.set(AppSettingKey.RADARR_URL, "http://new-radarr:7878");

        assertThat(service.get(AppSettingKey.RADARR_URL)).isEqualTo("http://new-radarr:7878");
    }
}
