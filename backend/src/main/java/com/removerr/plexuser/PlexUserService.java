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
import java.util.function.Predicate;

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

    /** Users whose account still has access to the Plex server. */
    public List<PlexUserResponse> getUsers() {
        return repository.findByServerAccessTrue().stream().map(this::toResponse).toList();
    }

    /**
     * Imports the server's local accounts, then flags which ones still have an accepted
     * share. Any failure aborts before touching access flags: a Plex outage must not
     * revoke everyone.
     */
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
                // Logged-in users get their plex.tv avatar at login; keep the local one for the others.
                if (u.getPlexTvId() == null) u.setPlexThumb(account.thumb());
                repository.save(u);
            } else {
                Optional<PlexUser> unlinkedOwner = repository.findByOwnerTrueAndPlexAccountIdIsNull()
                        .filter(owner -> matchesOwner(account, owner));
                if (unlinkedOwner.isPresent()) {
                    PlexUser owner = unlinkedOwner.get();
                    owner.setPlexAccountId(account.id());
                    repository.save(owner);
                } else {
                    PlexUser created = new PlexUser(account.id(), account.name());
                    created.setPlexThumb(account.thumb());
                    repository.save(created);
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
     * Finds the synced server account a plex.tv login belongs to. A shared user's local
     * account id may equal their plex.tv id; otherwise fall back to the owner heuristics.
     */
    public Optional<PlexUser> findLoginCandidate(long plexTvId, String username, String thumb) {
        List<PlexUser> unlinked = repository.findByPlexTvIdIsNull();
        return firstMatch(unlinked, u -> u.getPlexAccountId() != null && u.getPlexAccountId() == plexTvId)
                .or(() -> firstMatch(unlinked, u -> u.getName().equals(username)))
                .or(() -> firstMatch(unlinked, u -> u.getName().equalsIgnoreCase(username)))
                .or(() -> firstMatch(unlinked, u -> thumb != null && thumb.equals(u.getPlexThumb())));
    }

    private static Optional<PlexUser> firstMatch(List<PlexUser> users, Predicate<PlexUser> predicate) {
        return users.stream().filter(predicate).findFirst();
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
}
