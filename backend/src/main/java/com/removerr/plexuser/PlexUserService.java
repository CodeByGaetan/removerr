package com.removerr.plexuser;

import com.removerr.audit.AuditLogService;
import com.removerr.auth.PlexAuthClient;
import com.removerr.integration.ServiceNotConfiguredException;
import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.integration.plex.dto.PlexAccountEntry;
import com.removerr.plexuser.dto.PlexUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PlexUserService {

    private static final Logger log = LoggerFactory.getLogger(PlexUserService.class);

    private final PlexUserRepository repository;
    private final PlexLibraryClient plexClient;
    private final PlexAuthClient plexTvClient;
    private final AuditLogService auditLogService;

    public PlexUserService(PlexUserRepository repository, PlexLibraryClient plexClient,
                           PlexAuthClient plexTvClient, AuditLogService auditLogService) {
        this.repository = repository;
        this.plexClient = plexClient;
        this.plexTvClient = plexTvClient;
        this.auditLogService = auditLogService;
    }

    /**
     * Users whose account still has access to the Plex server. Non-admins only need names
     * for the "watched by" list: roles and login times are kept to admins.
     */
    public List<PlexUserResponse> getUsers(boolean withAccessDetails) {
        return repository.findByServerAccessTrue().stream()
                .map(u -> withAccessDetails ? toResponse(u) : toPublicResponse(u))
                .toList();
    }

    /**
     * Imports the server's local accounts, then flags which ones still have an accepted
     * share. Any failure aborts before touching access flags: a Plex outage must not
     * revoke everyone. New accounts and access changes alter who counts toward views, so the
     * cached library is evicted like in {@link #setCounted}.
     */
    @CacheEvict(value = {"library", "library-shows"}, allEntries = true)
    public void syncWithServer() {
        Optional<String> ownerToken = plexClient.findOwnerToken();
        if (ownerToken.isEmpty()) {
            log.info("No owner yet — skipping user sync");
            return;
        }
        List<PlexAccountEntry> accounts;
        Set<Long> sharedUserIds;
        try {
            accounts = plexClient.getAccounts();
            sharedUserIds = plexTvClient.getSharedUserIds(ownerToken.get(), plexClient.getMachineIdentifier());
        } catch (ServiceNotConfiguredException e) {
            log.info("Plex not configured — skipping user sync");
            return;
        }
        upsertAccounts(accounts);
        warnIfOwnerUnlinked();
        updateServerAccess(sharedUserIds);
    }

    // Not @Transactional: see setRole.
    void updateServerAccess(Set<Long> sharedUserIds) {
        for (PlexUser user : repository.findAll()) {
            boolean access = user.isOwner() || isShared(user, sharedUserIds);
            if (access == user.hasServerAccess()) continue;

            user.setServerAccess(access);
            Role previous = user.getRole();
            boolean revoked = !access && previous != Role.NONE;
            if (revoked) user.setRole(Role.NONE);
            repository.save(user);

            log.info("User '{}' {} the Plex server", user.getName(), access ? "regained access to" : "lost access to");
            if (revoked) {
                auditLogService.log(null, "ROLE_CHANGE", "USER", String.valueOf(user.getId()),
                        AuditLogService.meta("title", user.getName(), "from", previous, "to", Role.NONE));
            }
        }
    }

    // A shared user's local account id equals their plex.tv id (checked on a real server),
    // and logged-in users also carry their plex.tv id.
    private static boolean isShared(PlexUser user, Set<Long> sharedUserIds) {
        return (user.getPlexAccountId() != null && sharedUserIds.contains(user.getPlexAccountId().longValue()))
                || (user.getPlexTvId() != null && sharedUserIds.contains(user.getPlexTvId()));
    }

    void upsertAccounts(List<PlexAccountEntry> accounts) {
        for (PlexAccountEntry account : accounts.stream()
                .filter(a -> a.id() > 0 && a.name() != null && !a.name().isBlank())
                .toList()) {
            Optional<PlexUser> existing = repository.findByPlexAccountId(account.id());
            if (existing.isPresent()) {
                PlexUser u = existing.get();
                u.setName(account.name());
                repository.save(u);
            } else {
                Optional<PlexUser> unlinkedOwner = repository.findByOwnerTrueAndPlexAccountIdIsNull()
                        .filter(owner -> matchesOwner(account, owner));
                if (unlinkedOwner.isPresent()) {
                    PlexUser owner = unlinkedOwner.get();
                    owner.setPlexAccountId(account.id());
                    repository.save(owner);
                } else {
                    repository.save(new PlexUser(account.id(), account.name()));
                }
            }
        }
    }

    // Tries name strict, then name case-insensitive, then thumb URL — Plex /accounts
    // doesn't expose the global plex.tv user id, so we rely on these heuristics.
    private boolean matchesOwner(PlexAccountEntry account, PlexUser owner) {
        if (account.name().equals(owner.getName())) return true;
        if (account.name().equalsIgnoreCase(owner.getName())) return true;
        return owner.getPlexThumb() != null && owner.getPlexThumb().equals(account.thumb());
    }

    /**
     * The synced account a plex.tv login belongs to: a shared user's local account id is
     * their plex.tv id. Never fall back to name or avatar here — an outsider can register
     * a plex.tv account with the same name and take over a row that was granted a role.
     */
    public Optional<PlexUser> findLoginCandidate(long plexTvId) {
        if (plexTvId > Integer.MAX_VALUE) return Optional.empty();
        return repository.findByPlexAccountId((int) plexTvId)
                .filter(u -> u.getPlexTvId() == null && u.hasServerAccess());
    }

    private void warnIfOwnerUnlinked() {
        repository.findByOwnerTrueAndPlexAccountIdIsNull().ifPresent(owner ->
                log.warn("Owner '{}' (plexTvId={}) is not linked to a local Plex account. "
                        + "Their views will not be counted. Check that the plex.tv username matches "
                        + "the local server account name.", owner.getName(), owner.getPlexTvId()));
    }

    @Transactional
    @CacheEvict(value = {"library", "library-shows"}, allEntries = true)
    public PlexUserResponse setCounted(long id, boolean counted) {
        PlexUser user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setCounted(counted);
        return toResponse(repository.save(user));
    }

    // Not @Transactional: the audit log opens its own transaction (REQUIRES_NEW), which
    // would wait forever on the single SQLite connection held by an outer one.
    public PlexUserResponse setRole(long id, Role role, long actorId) {
        PlexUser user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (user.isOwner()) {
            throw new IllegalStateException("The owner's role cannot be changed");
        }
        if (user.getId() == actorId) {
            throw new IllegalStateException("You cannot change your own role");
        }
        if (!user.hasServerAccess()) {
            throw new IllegalStateException("This account no longer has access to the Plex server");
        }
        Role previous = user.getRole();
        if (previous == role) return toResponse(user);

        user.setRole(role);
        PlexUser saved = repository.save(user);
        auditLogService.log(actorId, "ROLE_CHANGE", "USER", String.valueOf(id),
                AuditLogService.meta("title", user.getName(), "from", previous, "to", role));
        return toResponse(saved);
    }

    private PlexUserResponse toResponse(PlexUser u) {
        return new PlexUserResponse(u.getId(), u.getPlexAccountId(), u.getName(),
                u.isOwner(), u.getRole(), u.isCounted(), u.getLastLoginAt());
    }

    private PlexUserResponse toPublicResponse(PlexUser u) {
        return new PlexUserResponse(u.getId(), null, u.getName(), u.isOwner(), null, u.isCounted(), null);
    }
}
