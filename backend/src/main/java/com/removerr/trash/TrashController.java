package com.removerr.trash;

import com.removerr.trash.dto.PurgeResult;
import com.removerr.trash.dto.TrashItemResponse;
import com.removerr.trash.dto.TrashMovieRequest;
import com.removerr.trash.dto.TrashSeasonRequest;
import com.removerr.trash.dto.TrashShowRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class TrashController {

    private final TrashService trashService;
    private final PurgeService purgeService;

    public TrashController(TrashService trashService, PurgeService purgeService) {
        this.trashService = trashService;
        this.purgeService = purgeService;
    }

    @PostMapping("/api/trash/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashMovie(@Valid @RequestBody TrashMovieRequest req,
                                        @AuthenticationPrincipal Long userId) throws IOException {
        return trashService.trashMovie(req.radarrId(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashShow(@Valid @RequestBody TrashShowRequest req,
                                       @AuthenticationPrincipal Long userId) throws IOException {
        return trashService.trashShow(req.sonarrId(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashSeason(@Valid @RequestBody TrashSeasonRequest req,
                                          @AuthenticationPrincipal Long userId) throws IOException {
        return trashService.trashSeason(req.sonarrId(), req.seasonNumber(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/{id}/restore")
    public TrashItemResponse restore(@PathVariable Long id,
                                     @AuthenticationPrincipal Long userId) throws IOException {
        return trashService.restore(id, userId);
    }

    @PostMapping("/api/admin/purge")
    public PurgeResult runPurge(@AuthenticationPrincipal Long userId) {
        return purgeService.purge(true, userId);
    }

    @GetMapping("/api/trash")
    public List<TrashItemResponse> getTrash() {
        return trashService.getTrash();
    }
}
