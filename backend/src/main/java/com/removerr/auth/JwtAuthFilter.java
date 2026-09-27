package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final AuthCookieFactory authCookies;
    private final PlexUserRepository plexUserRepository;

    public JwtAuthFilter(JwtService jwtService, AuthCookieFactory authCookies,
                         PlexUserRepository plexUserRepository) {
        this.jwtService = jwtService;
        this.authCookies = authCookies;
        this.plexUserRepository = plexUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        extractToken(request).ifPresent(token -> authenticate(token, request, response));
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request, HttpServletResponse response) {
        try {
            Claims claims = jwtService.parse(token);
            Long userId = Long.parseLong(claims.getSubject());

            // Role is read from the DB, not the token: revoking access or changing a role
            // takes effect on the next request, even with a long-lived sliding session.
            Optional<PlexUser> user = plexUserRepository.findById(userId)
                    .filter(u -> u.getRole() != Role.NONE);
            if (user.isEmpty()) {
                log.debug("JWT cookie for revoked or unknown user {} from {}", userId, clientIp(request));
                return;
            }

            var authority = new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name());
            var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);

            if (jwtService.needsRenewal(claims)) {
                response.addCookie(authCookies.withToken(jwtService.generateToken(userId)));
            }
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT cookie from {} on {}", clientIp(request), request.getRequestURI());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT cookie from {} on {}: {}",
                    clientIp(request), request.getRequestURI(), e.getMessage());
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthCookieFactory.COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
