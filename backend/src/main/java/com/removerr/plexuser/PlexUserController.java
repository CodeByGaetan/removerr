package com.removerr.plexuser;

import com.removerr.plexuser.dto.PlexUserResponse;
import com.removerr.plexuser.dto.UpdateUserRequest;
import org.springframework.http.HttpStatus;
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
    public PlexUserResponse updateUser(@PathVariable long id, @RequestBody UpdateUserRequest req) {
        if (req.counted() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "counted field is required");
        }
        return service.setCounted(id, req.counted());
    }
}
