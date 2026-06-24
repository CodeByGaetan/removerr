package com.removerr.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.removerr.integration.radarr.RadarrClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("dev")
@WireMockTest
class RadarrClientTest {

    @MockitoBean AppSettingService settingService;
    @Autowired RadarrClient radarrClient;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        when(settingService.get(AppSettingKey.RADARR_URL))
                .thenReturn("http://localhost:" + wm.getHttpPort());
        when(settingService.get(AppSettingKey.RADARR_API_KEY))
                .thenReturn("test-key");
    }

    @Test
    void getMovies_returnsDeserializedList(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/v3/movie"))
                .withHeader("X-Api-Key", equalTo("test-key"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 1,
                            "title": "Inception",
                            "year": 2010,
                            "tmdbId": 27205,
                            "hasFile": true,
                            "sizeOnDisk": 8000000000,
                            "added": "2023-01-01T00:00:00Z",
                            "path": "/data/movies/Inception",
                            "monitored": true,
                            "images": [{"coverType": "poster", "remoteUrl": "https://example.com/poster.jpg"}],
                            "movieFile": {"id": 10, "path": "/data/movies/Inception/Inception.mkv", "size": 8000000000}
                          }
                        ]
                        """)));

        List<RadarrMovie> movies = radarrClient.getMovies();

        assertThat(movies).hasSize(1);
        assertThat(movies.get(0).title()).isEqualTo("Inception");
        assertThat(movies.get(0).tmdbId()).isEqualTo(27205);
        assertThat(movies.get(0).posterUrl()).isEqualTo("https://example.com/poster.jpg");
    }

    @Test
    void getMovies_throwsOnUnauthorized(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/v3/movie"))
                .willReturn(unauthorized()));

        assertThatThrownBy(() -> radarrClient.getMovies())
                .isInstanceOf(IntegrationException.class);
    }

    @Test
    void getMovie_returnsById(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/api/v3/movie/42"))
                .withHeader("X-Api-Key", equalTo("test-key"))
                .willReturn(okJson("""
                        {"id": 42, "title": "Dune", "year": 2021,
                         "tmdbId": 438631, "hasFile": true,
                         "sizeOnDisk": 12000000000, "added": "2023-06-01T00:00:00Z",
                         "path": "/data/movies/Dune", "monitored": true}
                        """)));

        RadarrMovie movie = radarrClient.getMovie(42);

        assertThat(movie.id()).isEqualTo(42);
        assertThat(movie.title()).isEqualTo("Dune");
    }
}
