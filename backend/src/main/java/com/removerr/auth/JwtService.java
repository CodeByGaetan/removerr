package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    static final String CLAIM_ADMIN = "admin";
    private static final String ISSUER = "removerr";

    private final SecretKey signingKey;
    private final long expirationDays;

    public JwtService(
            @Value("${removerr.jwt.secret}") String secret,
            @Value("${removerr.jwt.expiration-days}") long expirationDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationDays = expirationDays;
    }

    public String generateToken(PlexUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ADMIN, user.isAdmin())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationDays, ChronoUnit.DAYS)))
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
}
