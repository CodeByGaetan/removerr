package com.removerr.auth;

import com.removerr.auth.dto.CreatePinResponse;
import com.removerr.auth.dto.MeResponse;
import com.removerr.auth.dto.PollPinResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          @Value("${removerr.cookie.secure:true}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/api/auth/plex/pin")
    public CreatePinResponse createPin() {
        return authService.createPin();
    }

    @GetMapping("/api/auth/plex/pin/{pinId}")
    public ResponseEntity<PollPinResponse> pollPin(@PathVariable long pinId,
                                                   HttpServletResponse response) {
        AuthService.PollResult result = authService.pollPin(pinId);

        if (result.response().status() == PollPinResponse.Status.SUCCESS) {
            addJwtCookie(response, result.jwt());
        }

        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addCookie(buildAuthCookie("", 0));
        return ResponseEntity.noContent().build();
    }

    // Protected by Spring Security (requires ROLE_ADMIN)
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return authService.getMe(userId);
    }

    private void addJwtCookie(HttpServletResponse response, String jwt) {
        response.addCookie(buildAuthCookie(jwt, 7 * 24 * 60 * 60));
    }

    // Attributes MUST match between set and clear, otherwise some browsers refuse the overwrite.
    private Cookie buildAuthCookie(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(JwtAuthFilter.COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(secureCookie);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
