package com.removerr.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

    static final String COOKIE_NAME = "removerr_token";
    static final String PIN_COOKIE_NAME = "removerr_pin";
    private static final String PIN_COOKIE_PATH = "/api/auth/plex/pin";

    private final JwtService jwtService;
    private final boolean secureCookie;

    public AuthCookieFactory(JwtService jwtService,
                             @Value("${removerr.cookie.secure:true}") boolean secureCookie) {
        this.jwtService = jwtService;
        this.secureCookie = secureCookie;
    }

    public Cookie withToken(String jwt) {
        return build(jwt, (int) jwtService.validity().toSeconds());
    }

    public Cookie cleared() {
        return build("", 0);
    }

    /** Ties a pending Plex PIN to the browser that created it; only sent to the PIN routes. */
    public Cookie withPinNonce(String nonce) {
        return build(PIN_COOKIE_NAME, PIN_COOKIE_PATH, nonce, (int) AuthService.PIN_TTL.toSeconds());
    }

    public Cookie clearedPinNonce() {
        return build(PIN_COOKIE_NAME, PIN_COOKIE_PATH, "", 0);
    }

    private Cookie build(String value, int maxAgeSeconds) {
        return build(COOKIE_NAME, "/", value, maxAgeSeconds);
    }

    // Attributes MUST match between set and clear, otherwise some browsers refuse the overwrite.
    private Cookie build(String name, String path, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath(path);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(secureCookie);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
