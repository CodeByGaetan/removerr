package com.removerr.plexuser;

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

@Service
public class PlexUserService {

    private static final Logger log = LoggerFactory.getLogger(PlexUserService.class);

    private final PlexUserRepository repository;
    private final PlexLibraryClient plexClient;

    public PlexUserService(PlexUserRepository repository, PlexLibraryClient plexClient) {
        this.repository = repository;
        this.plexClient = plexClient;
    }

    public List<PlexUserResponse> syncAndGetUsers() {
        List<PlexAccountEntry> accounts = List.of();
        try {
            accounts = plexClient.getAccounts();
        } catch (ServiceNotConfiguredException e) {
            log.info("Plex not configured — skipping user sync");
        } catch (Exception e) {
            log.warn("Failed to sync Plex users: {}", e.getMessage());
        }

        if (!accounts.isEmpty()) {
            upsertAccounts(accounts);
            warnIfAdminUnlinked();
        }
        return repository.findAll().stream().map(this::toResponse).toList();
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
                Optional<PlexUser> unlinkedAdmin = repository.findByAdminTrueAndPlexAccountIdIsNull()
                        .filter(admin -> matchesAdmin(account, admin));
                if (unlinkedAdmin.isPresent()) {
                    PlexUser admin = unlinkedAdmin.get();
                    admin.setPlexAccountId(account.id());
                    repository.save(admin);
                } else {
                    repository.save(new PlexUser(account.id(), account.name()));
                }
            }
        }
    }

    // Tries name strict, then name case-insensitive, then thumb URL — Plex /accounts
    // doesn't expose the global plex.tv user id, so we rely on these heuristics.
    private boolean matchesAdmin(PlexAccountEntry account, PlexUser admin) {
        if (account.name().equals(admin.getName())) return true;
        if (account.name().equalsIgnoreCase(admin.getName())) return true;
        return admin.getPlexThumb() != null && admin.getPlexThumb().equals(account.thumb());
    }

    private void warnIfAdminUnlinked() {
        repository.findByAdminTrueAndPlexAccountIdIsNull().ifPresent(admin ->
                log.warn("Admin '{}' (plexTvId={}) is not linked to a local Plex account. "
                        + "Their views will not be counted. Check that the plex.tv username matches "
                        + "the local server account name.", admin.getName(), admin.getPlexTvId()));
    }

    @Transactional
    @CacheEvict(value = {"library", "library-shows"}, allEntries = true)
    public PlexUserResponse setCounted(long id, boolean counted) {
        PlexUser user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setCounted(counted);
        return toResponse(repository.save(user));
    }

    private PlexUserResponse toResponse(PlexUser u) {
        return new PlexUserResponse(u.getId(), u.getPlexAccountId(), u.getName(),
                u.isAdmin(), u.isCounted());
    }
}
