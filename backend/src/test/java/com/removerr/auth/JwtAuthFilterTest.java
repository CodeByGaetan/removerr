package com.removerr.auth;

import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String TOKEN = "token";
    private static final long USER_ID = 42L;

    private final JwtService jwtService = mock(JwtService.class);
    private final AuthCookieFactory authCookies = mock(AuthCookieFactory.class);
    private final PlexUserRepository repository = mock(PlexUserRepository.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService, authCookies, repository);

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(USER_ID));
        when(jwtService.parse(TOKEN)).thenReturn(claims);
        when(jwtService.needsRenewal(any())).thenReturn(true);
        when(jwtService.generateToken(USER_ID)).thenReturn("renewed");
        when(authCookies.withToken("renewed")).thenReturn(new Cookie(AuthCookieFactory.COOKIE_NAME, "renewed"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsAuthorityMatchingRoleFromDatabase() throws Exception {
        givenUserWithRole(Role.USER);

        runFilter();

        assertThat(authentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(response.getCookie(AuthCookieFactory.COOKIE_NAME)).isNotNull();
    }

    @Test
    void ignoresTokenOfRevokedUserAndDoesNotRenewIt() throws Exception {
        givenUserWithRole(Role.NONE);

        runFilter();

        assertThat(authentication()).isNull();
        assertThat(response.getCookie(AuthCookieFactory.COOKIE_NAME)).isNull();
    }

    @Test
    void ignoresTokenOfDeletedUser() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        runFilter();

        assertThat(authentication()).isNull();
    }

    @Test
    void skipsDatabaseLookupOutsideApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/main-ABC123.js");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_NAME, TOKEN));

        filter.doFilter(request, response, new MockFilterChain());

        verifyNoInteractions(repository);
        assertThat(authentication()).isNull();
    }

    private void givenUserWithRole(Role role) {
        PlexUser user = new PlexUser(1, "someone");
        user.setRole(role);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/library");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_NAME, TOKEN));
        filter.doFilter(request, response, new MockFilterChain());
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
