package com.removerr.auth;

import com.removerr.auth.dto.CreatePinResponse;
import com.removerr.auth.dto.MeResponse;
import com.removerr.auth.dto.PollPinResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookies;

    public AuthController(AuthService authService, AuthCookieFactory authCookies) {
        this.authService = authService;
        this.authCookies = authCookies;
    }

    @PostMapping("/api/auth/plex/pin")
    public CreatePinResponse createPin(HttpServletResponse response) {
        AuthService.PinStart start = authService.createPin();
        response.addCookie(authCookies.withPinNonce(start.nonce()));
        return start.response();
    }

    @GetMapping("/api/auth/plex/pin/{pinId}")
    public ResponseEntity<PollPinResponse> pollPin(@PathVariable long pinId,
                                                   @CookieValue(name = AuthCookieFactory.PIN_COOKIE_NAME, required = false)
                                                   String pinNonce,
                                                   HttpServletResponse response) {
        AuthService.PollResult result = authService.pollPin(pinId, pinNonce);

        if (result.response().status() == PollPinResponse.Status.SUCCESS) {
            response.addCookie(authCookies.withToken(result.jwt()));
            response.addCookie(authCookies.clearedPinNonce());
        }

        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addCookie(authCookies.cleared());
        return ResponseEntity.noContent().build();
    }

    // Protected by Spring Security (USER or ADMIN)
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return authService.getMe(userId);
    }
}
