package com.removerr.integration.radarr;

import com.removerr.integration.ArrBaseClient;
import com.removerr.integration.radarr.dto.RadarrMovie;
import com.removerr.setting.AppSettingKey;
import com.removerr.setting.AppSettingService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RadarrClient extends ArrBaseClient {

    public RadarrClient(AppSettingService settings) {
        super(settings, AppSettingKey.RADARR_URL, AppSettingKey.RADARR_API_KEY);
    }

    public List<RadarrMovie> getMovies() {
        return getList("/api/v3/movie", new ParameterizedTypeReference<>() {});
    }

    public RadarrMovie getMovie(int id) {
        return get("/api/v3/movie/" + id, RadarrMovie.class);
    }

    public RadarrMovie getMovieOrNull(int id) {
        return getOrNull("/api/v3/movie/" + id, RadarrMovie.class);
    }

    public void deleteMovie(int id, boolean deleteFiles) {
        delete("/api/v3/movie/" + id + "?deleteFiles=" + deleteFiles + "&addImportExclusion=false");
    }
}
