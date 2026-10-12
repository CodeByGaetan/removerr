package com.removerr.auth;

import com.removerr.auth.dto.PlexPin;
import com.removerr.auth.dto.PlexUserAccount;
import com.removerr.auth.dto.PollPinResponse;
import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.PlexUserService;
import com.removerr.plexuser.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final long PIN_ID = 1L;
    private static final String TOKEN = "plex-token";
    private static final PlexUserAccount ACCOUNT = new PlexUserAccount(555L, "marie", "m@x.io", "thumb");

    private final PlexAuthClient plexClient = mock(PlexAuthClient.class);
    private final PlexUserRepository repository = mock(PlexUserRepository.class);
    private final AesGcmEncryptor encryptor = mock(AesGcmEncryptor.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final PlexUserService plexUserService = mock(PlexUserService.class);
    private final AuthService authService =
            new AuthService(plexClient, repository, encryptor, jwtService, plexUserService,
                    mock(PlatformTransactionManager.class), mock(PlexLibraryClient.class));

    private String nonce;

    @BeforeEach
    void setUp() {
        when(plexClient.createPin()).thenReturn(new PlexPin(PIN_ID, "code", null, null));
        nonce = authService.createPin().nonce();
        when(plexClient.checkPin(PIN_ID)).thenReturn(new PlexPin(PIN_ID, "code", TOKEN, null));
        when(plexClient.getUserInfo(TOKEN)).thenReturn(ACCOUNT);
        when(repository.findByPlexTvId(ACCOUNT.id())).thenReturn(Optional.empty());
        // A persisted entity has an id; the response built from it needs one.
        when(repository.save(any())).thenAnswer(inv -> {
            PlexUser persisted = spy((PlexUser) inv.getArgument(0));
            doReturn(99L).when(persisted).getId();
            return persisted;
        });
        when(encryptor.encrypt(TOKEN)).thenReturn("encrypted");
        when(plexUserService.findLoginCandidate(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void firstLoginEver_createsOwnerWithToken() {
        when(repository.existsByOwnerTrue()).thenReturn(false);

        PlexUser user = login();

        assertThat(user.isOwner()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPlexTokenEncrypted()).isEqualTo("encrypted");
    }

    @Test
    void syncedAccountWithAccess_isLinkedWithoutKeepingItsToken() {
        PlexUser synced = syncedUser(Role.USER);
        when(repository.existsByOwnerTrue()).thenReturn(true);
        when(plexUserService.findLoginCandidate(ACCOUNT.id())).thenReturn(Optional.of(synced));

        PlexUser user = login();

        assertThat(user).isSameAs(synced);
        assertThat(user.getPlexTvId()).isEqualTo(ACCOUNT.id());
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getPlexTokenEncrypted()).isNull();
    }

    @Test
    void syncedAccountWithoutAccess_isDeniedAndLeftUntouched() {
        PlexUser synced = syncedUser(Role.NONE);
        when(repository.existsByOwnerTrue()).thenReturn(true);
        when(plexUserService.findLoginCandidate(ACCOUNT.id())).thenReturn(Optional.of(synced));

        assertThatThrownBy(() -> authService.pollPin(PIN_ID, nonce)).isInstanceOf(LoginDeniedException.class);
        assertThat(synced.getPlexTvId()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void unknownAccount_isDenied() {
        when(repository.existsByOwnerTrue()).thenReturn(true);

        assertThatThrownBy(() -> authService.pollPin(PIN_ID, nonce)).isInstanceOf(LoginDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void revokedUser_isDeniedOnReLogin() {
        PlexUser revoked = syncedUser(Role.NONE);
        revoked.setPlexTvId(ACCOUNT.id());
        when(repository.findByPlexTvId(ACCOUNT.id())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.pollPin(PIN_ID, nonce)).isInstanceOf(LoginDeniedException.class);
    }

    @Test
    void pollFromAnotherBrowser_isExpiredAndNeverReachesPlex() {
        assertThat(authService.pollPin(PIN_ID, "stolen-or-missing").response().status())
                .isEqualTo(PollPinResponse.Status.EXPIRED);
        assertThat(authService.pollPin(PIN_ID, null).response().status())
                .isEqualTo(PollPinResponse.Status.EXPIRED);
        verify(plexClient, never()).checkPin(anyLong());
    }

    @Test
    void unknownPin_isExpiredAndNeverReachesPlex() {
        assertThat(authService.pollPin(PIN_ID + 1, nonce).response().status())
                .isEqualTo(PollPinResponse.Status.EXPIRED);
        verify(plexClient, never()).checkPin(anyLong());
    }

    @Test
    void approvedPin_givesASingleSession() {
        when(repository.existsByOwnerTrue()).thenReturn(false);

        assertThat(authService.pollPin(PIN_ID, nonce).response().status())
                .isEqualTo(PollPinResponse.Status.SUCCESS);
        assertThat(authService.pollPin(PIN_ID, nonce).response().status())
                .isEqualTo(PollPinResponse.Status.EXPIRED);
    }

    private PlexUser login() {
        AuthService.PollResult result = authService.pollPin(PIN_ID, nonce);
        assertThat(result.response().status()).isEqualTo(PollPinResponse.Status.SUCCESS);
        ArgumentCaptor<PlexUser> saved = ArgumentCaptor.forClass(PlexUser.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static PlexUser syncedUser(Role role) {
        PlexUser user = new PlexUser(12, "Marie");
        user.setRole(role);
        return user;
    }
}
