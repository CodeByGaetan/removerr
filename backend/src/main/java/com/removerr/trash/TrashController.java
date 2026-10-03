package com.removerr.trash;

import com.removerr.job.JobRunner;
import com.removerr.trash.dto.PurgeResult;
import com.removerr.trash.dto.TrashItemResponse;
import com.removerr.trash.dto.TrashMovieRequest;
import com.removerr.trash.dto.TrashSeasonRequest;
import com.removerr.trash.dto.TrashShowRequest;
import com.removerr.plexuser.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
public class TrashController {

    private final TrashService trashService;
    private final PurgeJob purgeJob;
    private final JobRunner jobRunner;

    public TrashController(TrashService trashService, PurgeJob purgeJob, JobRunner jobRunner) {
        this.trashService = trashService;
        this.purgeJob = purgeJob;
        this.jobRunner = jobRunner;
    }

    @PostMapping("/api/trash/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashMovie(@Valid @RequestBody TrashMovieRequest req,
                                        @AuthenticationPrincipal Long userId,
                                        HttpServletRequest request) throws IOException {
        rejectImmediateUnlessAdmin(req.immediate(), request);
        return trashService.trashMovie(req.radarrId(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashShow(@Valid @RequestBody TrashShowRequest req,
                                       @AuthenticationPrincipal Long userId,
                                       HttpServletRequest request) throws IOException {
        rejectImmediateUnlessAdmin(req.immediate(), request);
        return trashService.trashShow(req.sonarrId(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/seasons")
    @ResponseStatus(HttpStatus.CREATED)
    public TrashItemResponse trashSeason(@Valid @RequestBody TrashSeasonRequest req,
                                          @AuthenticationPrincipal Long userId,
                                          HttpServletRequest request) throws IOException {
        rejectImmediateUnlessAdmin(req.immediate(), request);
        return trashService.trashSeason(req.sonarrId(), req.seasonNumber(), req.immediate(), userId);
    }

    @PostMapping("/api/trash/{id}/restore")
    public TrashItemResponse restore(@PathVariable Long id,
                                     @AuthenticationPrincipal Long userId) throws IOException {
        return trashService.restore(id, userId);
    }

    @PostMapping("/api/admin/purge")
    public PurgeResult runPurge(@AuthenticationPrincipal Long userId) {
        return jobRunner.runNow(purgeJob, userId);
    }

    @GetMapping("/api/trash")
    public List<TrashItemResponse> getTrash() {
        return trashService.getTrash();
    }

    // Immediate deletion skips the retention delay, the only safety net left to other users.
    private void rejectImmediateUnlessAdmin(boolean immediate, HttpServletRequest request) {
        if (immediate && !request.isUserInRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Immediate deletion is reserved to admins");
        }
    }
}
