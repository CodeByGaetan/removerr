package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    private final JwtService jwtService = new JwtService(SECRET, 7);

    private static PlexUser user(long id, boolean admin) {
        PlexUser user = mock(PlexUser.class);
        when(user.getId()).thenReturn(id);
        when(user.isAdmin()).thenReturn(admin);
        return user;
    }

    @Test
    void generateThenParse_roundtripsSubjectAndAdminClaim() {
        Claims claims = jwtService.parse(jwtService.generateToken(user(7, true)));

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get(JwtService.CLAIM_ADMIN, Boolean.class)).isTrue();
    }

    @Test
    void parse_rejectsTokenSignedWithAnotherSecret() {
        String forged = new JwtService("another-secret-another-secret-xyz-123", 7)
                .generateToken(user(1, false));

        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsExpiredToken() {
        // Negative expiration → the token is already expired when issued.
        String expired = new JwtService(SECRET, -1).generateToken(user(1, false));

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
