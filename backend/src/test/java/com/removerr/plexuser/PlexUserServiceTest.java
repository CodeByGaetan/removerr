package com.removerr.plexuser;

import com.removerr.audit.AuditLogService;
import com.removerr.auth.PlexAuthClient;
import com.removerr.auth.dto.PlexHomeUser;
import com.removerr.integration.IntegrationException;
import com.removerr.integration.plex.PlexLibraryClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlexUserServiceTest {

    private static final long ACTOR_ID = 1L;
    private static final long TARGET_ID = 2L;

    private final PlexUserRepository repository = mock(PlexUserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final PlexLibraryClient plexClient = mock(PlexLibraryClient.class);
    private final PlexAuthClient plexTvClient = mock(PlexAuthClient.class);
    private final PlexUserService service =
            new PlexUserService(repository, plexClient, plexTvClient, auditLogService);

    @Test
    void setRole_updatesRoleAndLogsChange() {
        PlexUser target = givenUser(TARGET_ID, false, Role.NONE);

        service.setRole(TARGET_ID, Role.USER, ACTOR_ID);

        assertThat(target.getRole()).isEqualTo(Role.USER);
        verify(repository).save(target);
        verify(auditLogService).log(eq(ACTOR_ID), eq("ROLE_CHANGE"), eq("USER"), eq("2"), anyMap());
    }

    @Test
    void setRole_rejectsOwner() {
        givenUser(TARGET_ID, true, Role.ADMIN);

        assertThatThrownBy(() -> service.setRole(TARGET_ID, Role.NONE, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void setRole_rejectsOwnRole() {
        givenUser(ACTOR_ID, false, Role.ADMIN);

        assertThatThrownBy(() -> service.setRole(ACTOR_ID, Role.USER, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void setRole_doesNothingWhenRoleIsUnchanged() {
        givenUser(TARGET_ID, false, Role.USER);

        service.setRole(TARGET_ID, Role.USER, ACTOR_ID);

        verify(repository, never()).save(any());
        verify(auditLogService, never()).log(any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void findLoginCandidate_matchesLocalAccountIdEqualToPlexTvId() {
        PlexUser marie = synced(555, "marie", null);
        when(repository.findByPlexAccountId(555)).thenReturn(Optional.of(marie));

        assertThat(service.findLoginCandidate(555)).contains(marie);
    }

    @Test
    void findLoginCandidate_neverMatchesOnNameAlone() {
        // An outsider registering the plex.tv username "marie" gets a different id.
        when(repository.findByPlexAccountId(999)).thenReturn(Optional.empty());

        assertThat(service.findLoginCandidate(999)).isEmpty();
    }

    @Test
    void findLoginCandidate_ignoresAlreadyLinkedOrUnsharedAccounts() {
        PlexUser linked = synced(555, "marie", null);
        linked.setPlexTvId(555);
        PlexUser unshared = synced(556, "hugo", null);
        unshared.setServerAccess(ServerAccess.NONE);
        when(repository.findByPlexAccountId(555)).thenReturn(Optional.of(linked));
        when(repository.findByPlexAccountId(556)).thenReturn(Optional.of(unshared));

        assertThat(service.findLoginCandidate(555)).isEmpty();
        assertThat(service.findLoginCandidate(556)).isEmpty();
    }

    @Test
    void updateServerAccess_revokesRoleOfUnsharedUser() {
        PlexUser hugo = synced(35867154, "Hugo", null);
        hugo.setRole(Role.USER);
        when(repository.findAll()).thenReturn(List.of(hugo));

        service.updateServerAccess(Map.of(176554347L, ServerAccess.SHARED));

        assertThat(hugo.getServerAccess()).isEqualTo(ServerAccess.NONE);
        assertThat(hugo.getRole()).isEqualTo(Role.NONE);
        verify(repository).save(hugo);
        verify(auditLogService).log(eq(null), eq("ROLE_CHANGE"), eq("USER"), any(), anyMap());
    }

    @Test
    void updateServerAccess_restoresAccessWithoutRestoringRole() {
        PlexUser hugo = synced(35867154, "Hugo", null);
        hugo.setServerAccess(ServerAccess.NONE);
        when(repository.findAll()).thenReturn(List.of(hugo));

        service.updateServerAccess(Map.of(35867154L, ServerAccess.SHARED));

        assertThat(hugo.getServerAccess()).isEqualTo(ServerAccess.SHARED);
        assertThat(hugo.getRole()).isEqualTo(Role.NONE);
        verify(auditLogService, never()).log(any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void accessGrants_homeWinsOverShareFlagsManagedUsersAndSkipsGuest() {
        Map<Long, ServerAccess> grants = PlexUserService.accessGrants(
                Set.of(1L, 2L),
                List.of(new PlexHomeUser(2L, false, false), new PlexHomeUser(3L, true, false),
                        new PlexHomeUser(4L, true, true)));

        assertThat(grants).containsExactlyInAnyOrderEntriesOf(Map.of(
                1L, ServerAccess.SHARED, 2L, ServerAccess.HOME, 3L, ServerAccess.MANAGED));
    }

    @Test
    void updateServerAccess_revokesRoleOfUserBecomingManaged() {
        PlexUser kid = synced(42, "Kid", null);
        kid.setRole(Role.USER);
        when(repository.findAll()).thenReturn(List.of(kid));

        service.updateServerAccess(Map.of(42L, ServerAccess.MANAGED));

        assertThat(kid.getServerAccess()).isEqualTo(ServerAccess.MANAGED);
        assertThat(kid.getRole()).isEqualTo(Role.NONE);
    }

    @Test
    void updateServerAccess_keepsRoleOfUserMovingFromShareToHome() {
        PlexUser partner = synced(43, "Partner", null);
        partner.setRole(Role.ADMIN);
        when(repository.findAll()).thenReturn(List.of(partner));

        service.updateServerAccess(Map.of(43L, ServerAccess.HOME));

        assertThat(partner.getServerAccess()).isEqualTo(ServerAccess.HOME);
        assertThat(partner.getRole()).isEqualTo(Role.ADMIN);
        verify(auditLogService, never()).log(any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void setRole_rejectsManagedUser() {
        PlexUser target = givenUser(TARGET_ID, false, Role.NONE);
        target.setServerAccess(ServerAccess.MANAGED);

        assertThatThrownBy(() -> service.setRole(TARGET_ID, Role.USER, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateServerAccess_keepsOwnerAndUnchangedUsersUntouched() {
        PlexUser owner = new PlexUser(1L, "owner", null, null, null, null);
        PlexUser emilien = synced(176554347, "Emilien", null);
        when(repository.findAll()).thenReturn(List.of(owner, emilien));

        service.updateServerAccess(Map.of(176554347L, ServerAccess.SHARED));

        assertThat(owner.getServerAccess()).isEqualTo(ServerAccess.OWNER);
        verify(repository, never()).save(any());
    }

    @Test
    void syncWithServer_leavesAccessUntouchedWhenPlexTvFails() {
        when(plexClient.findOwnerToken()).thenReturn(Optional.of("token"));
        when(plexClient.getMachineIdentifier()).thenReturn("machine");
        when(plexTvClient.getSharedUserIds("token", "machine")).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(service::syncWithServer).hasMessage("down");
        verify(repository, never()).save(any());
    }

    @Test
    void syncWithServer_leavesEverythingUntouchedWhenPlexServerIsUnreachable() {
        when(plexClient.findOwnerToken()).thenReturn(Optional.of("token"));
        when(plexClient.getAccounts()).thenThrow(new IntegrationException("Connection refused"));

        assertThatThrownBy(service::syncWithServer).isInstanceOf(IntegrationException.class);
        verify(repository, never()).save(any());
        verify(plexTvClient, never()).getSharedUserIds(anyString(), anyString());
    }

    @Test
    void syncWithServer_skipsBeforeFirstLogin() {
        when(plexClient.findOwnerToken()).thenReturn(Optional.empty());

        service.syncWithServer();

        verify(plexClient, never()).getAccounts();
    }

    @Test
    void setRole_rejectsUserWithoutServerAccess() {
        PlexUser target = givenUser(TARGET_ID, false, Role.NONE);
        target.setServerAccess(ServerAccess.NONE);

        assertThatThrownBy(() -> service.setRole(TARGET_ID, Role.USER, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getUsers_hidesAccessDetailsFromNonAdmins() {
        PlexUser marie = spy(synced(555, "marie", null));
        when(marie.getId()).thenReturn(7L);
        marie.setRole(Role.ADMIN);
        marie.setLastLoginAt("2026-10-12T10:00:00Z");
        when(repository.findByServerAccessNot(ServerAccess.NONE)).thenReturn(List.of(marie));

        var publicView = service.getUsers(false).getFirst();
        var adminView = service.getUsers(true).getFirst();

        assertThat(publicView.name()).isEqualTo("marie");
        assertThat(publicView.role()).isNull();
        assertThat(publicView.lastLoginAt()).isNull();
        assertThat(publicView.plexAccountId()).isNull();
        assertThat(adminView.role()).isEqualTo(Role.ADMIN);
        assertThat(adminView.lastLoginAt()).isNotNull();
    }

    private static PlexUser synced(int plexAccountId, String name, String thumb) {
        PlexUser user = new PlexUser(plexAccountId, name);
        user.setServerAccess(ServerAccess.SHARED);
        user.setPlexThumb(thumb);
        return user;
    }

    private PlexUser givenUser(long id, boolean owner, Role role) {
        PlexUser user = spy(owner
                ? new PlexUser(id, "owner", null, null, null, null)
                : new PlexUser((int) id, "someone"));
        when(user.getId()).thenReturn(id);
        user.setRole(role);
        if (!owner) user.setServerAccess(ServerAccess.SHARED);
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        return user;
    }
}
