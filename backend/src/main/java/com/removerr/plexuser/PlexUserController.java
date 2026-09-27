package com.removerr.plexuser;

import com.removerr.plexuser.dto.PlexUserResponse;
import com.removerr.plexuser.dto.UpdateUserRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class PlexUserController {

    private final PlexUserService service;

    public PlexUserController(PlexUserService service) {
        this.service = service;
    }

    @GetMapping("/api/users")
    public List<PlexUserResponse> getUsers() {
        return service.syncAndGetUsers();
    }

    @PatchMapping("/api/users/{id}")
    public PlexUserResponse updateUser(@PathVariable long id, @RequestBody UpdateUserRequest req,
                                       @AuthenticationPrincipal Long actorId) {
        if (req.counted() == null && req.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "counted or role field is required");
        }
        PlexUserResponse updated = null;
        if (req.role() != null) updated = service.setRole(id, req.role(), actorId);
        if (req.counted() != null) updated = service.setCounted(id, req.counted());
        return updated;
    }
}
