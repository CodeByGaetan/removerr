package com.removerr.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

    static final String COOKIE_NAME = "removerr_token";

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

    // Attributes MUST match between set and clear, otherwise some browsers refuse the overwrite.
    private Cookie build(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(secureCookie);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
