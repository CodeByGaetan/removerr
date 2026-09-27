package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    private final JwtService jwtService = new JwtService(SECRET, 7);

    private static PlexUser user(long id) {
        PlexUser user = mock(PlexUser.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    @Test
    void generateThenParse_roundtripsSubject() {
        Claims claims = jwtService.parse(jwtService.generateToken(user(7)));

        assertThat(claims.getSubject()).isEqualTo("7");
    }

    @Test
    void parse_rejectsTokenSignedWithAnotherSecret() {
        String forged = new JwtService("another-secret-another-secret-xyz-123", 7)
                .generateToken(user(1));

        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsExpiredToken() {
        // Negative expiration → the token is already expired when issued.
        String expired = new JwtService(SECRET, -1).generateToken(user(1));

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void needsRenewal_falseForFreshToken() {
        Claims claims = jwtService.parse(jwtService.generateToken(user(1)));

        assertThat(jwtService.needsRenewal(claims)).isFalse();
    }

    @Test
    void needsRenewal_trueOncePastHalfLifetime() {
        Instant issuedAt = Instant.now().minus(Duration.ofDays(4)); // validity = 7 days
        Claims claims = Jwts.claims().issuedAt(Date.from(issuedAt)).build();

        assertThat(jwtService.needsRenewal(claims)).isTrue();
    }
}
