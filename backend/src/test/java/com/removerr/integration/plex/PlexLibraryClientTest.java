package com.removerr.integration.plex;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.integration.IntegrationException;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest
class PlexLibraryClientTest {

    private final AppSettingService settings = mock(AppSettingService.class);
    private final PlexUserRepository repository = mock(PlexUserRepository.class);
    private final AesGcmEncryptor encryptor = mock(AesGcmEncryptor.class);
    private final PlexLibraryClient client = new PlexLibraryClient(settings, repository, encryptor);

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        when(settings.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn("http://localhost:" + wm.getHttpPort());
        PlexUser owner = new PlexUser(1L, "owner", null, null, "encrypted", null);
        when(repository.findByOwnerTrue()).thenReturn(Optional.of(owner));
        when(encryptor.decrypt("encrypted")).thenReturn("owner-token");
    }

    @Test
    void getViewHistory_requestsNewestFirstSoTheLimitDropsOldViews() {
        stubFor(get(urlPathEqualTo("/status/sessions/history/all"))
                .withQueryParam("sort", equalTo("viewedAt:desc"))
                .withQueryParam("X-Plex-Container-Size", equalTo("10000"))
                .withHeader("X-Plex-Token", equalTo("owner-token"))
                .willReturn(okJson("{\"MediaContainer\": {\"Metadata\": []}}")));

        assertThat(client.getViewHistory(10_000)).isEmpty();
    }

    @Test
    void serverErrorBecomesIntegrationException() {
        stubFor(get(urlPathEqualTo("/library/sections")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(client::getSections).isInstanceOf(IntegrationException.class);
    }

    @Test
    void unreachableServerBecomesIntegrationException() {
        // Nothing listens on port 1: the connection is refused.
        when(settings.get(AppSettingKey.PLEX_SERVER_URL)).thenReturn("http://localhost:1");

        assertThatThrownBy(client::getSections).isInstanceOf(IntegrationException.class);
    }
}
