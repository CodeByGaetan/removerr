package com.removerr.plexuser;

import com.removerr.audit.AuditLogService;
import com.removerr.integration.plex.PlexLibraryClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    private final PlexUserService service =
            new PlexUserService(repository, mock(PlexLibraryClient.class), auditLogService);

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

    private PlexUser givenUser(long id, boolean owner, Role role) {
        PlexUser user = spy(owner
                ? new PlexUser(id, "owner", null, null, null, null)
                : new PlexUser((int) id, "someone"));
        when(user.getId()).thenReturn(id);
        user.setRole(role);
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        return user;
    }
}
