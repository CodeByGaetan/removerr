package com.removerr.library;

import com.removerr.library.dto.MediaCard;
import com.removerr.library.dto.ShowCard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LibraryController {

    private final MediaAggregationService aggregationService;

    public LibraryController(MediaAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @GetMapping("/api/library")
    public List<MediaCard> getMovies() {
        return aggregationService.getMovies();
    }

    @GetMapping("/api/library/shows")
    public List<ShowCard> getShows() {
        return aggregationService.getSeries();
    }
}
