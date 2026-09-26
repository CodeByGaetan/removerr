package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    static final String CLAIM_ADMIN = "admin";
    private static final String ISSUER = "removerr";

    private final SecretKey signingKey;
    private final Duration validity;

    public JwtService(
            @Value("${removerr.jwt.secret}") String secret,
            @Value("${removerr.jwt.expiration-days}") long expirationDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofDays(expirationDays);
    }

    public Duration validity() {
        return validity;
    }

    public String generateToken(PlexUser user) {
        return generateToken(user.getId(), user.isAdmin());
    }

    public String generateToken(long userId, boolean admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(CLAIM_ADMIN, admin)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(signingKey)
                .compact();
    }

    // Throws ExpiredJwtException / JwtException / IllegalArgumentException on failure.
    // Caller is responsible for catching and logging — keeps this service free of
    // Servlet concerns (IP, request info) needed for security logs.
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Sliding session: once past half its lifetime, the token is reissued on the next
    // request, so an app opened regularly (PWA) never hits the login screen again.
    public boolean needsRenewal(Claims claims) {
        Instant issuedAt = claims.getIssuedAt().toInstant();
        return Instant.now().isAfter(issuedAt.plus(validity.dividedBy(2)));
    }
}
