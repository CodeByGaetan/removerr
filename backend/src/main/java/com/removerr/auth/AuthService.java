package com.removerr.auth;

import com.removerr.auth.dto.*;
import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.PlexUserService;
import com.removerr.plexuser.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private final PlexAuthClient plexClient;
    private final PlexUserRepository plexUserRepository;
    private final AesGcmEncryptor encryptor;
    private final JwtService jwtService;
    private final PlexUserService plexUserService;

    public AuthService(PlexAuthClient plexClient, PlexUserRepository plexUserRepository,
                       AesGcmEncryptor encryptor, JwtService jwtService, PlexUserService plexUserService) {
        this.plexClient = plexClient;
        this.plexUserRepository = plexUserRepository;
        this.encryptor = encryptor;
        this.jwtService = jwtService;
        this.plexUserService = plexUserService;
    }

    public CreatePinResponse createPin() {
        PlexPin pin = plexClient.createPin();
        return new CreatePinResponse(pin.id(), pin.code(), plexClient.buildAuthUrl(pin.code()));
    }

    @Transactional
    public PollResult pollPin(long pinId) {
        PlexPin pin = plexClient.checkPin(pinId);

        if (pin == null) return PollResult.expired();
        if (pin.authToken() == null) return PollResult.pending();

        PlexUserAccount info = plexClient.getUserInfo(pin.authToken());
        PlexUser user = resolveUser(info, pin.authToken());
        String jwt = jwtService.generateToken(user);
        int countedTotal = (int) plexUserRepository.countByCountedTrueAndServerAccessTrue();
        return PollResult.success(toMeResponse(user, countedTotal), jwt);
    }

    public MeResponse getMe(Long userId) {
        int countedTotal = (int) plexUserRepository.countByCountedTrueAndServerAccessTrue();
        return plexUserRepository.findById(userId)
                .map(user -> toMeResponse(user, countedTotal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record PollResult(PollPinResponse response, String jwt) {
        static PollResult pending() { return new PollResult(PollPinResponse.pending(), null); }
        static PollResult expired() { return new PollResult(PollPinResponse.expired(), null); }
        static PollResult success(MeResponse user, String jwt) {
            return new PollResult(PollPinResponse.success(user), jwt);
        }
    }

    private PlexUser resolveUser(PlexUserAccount info, String plexToken) {
        PlexUser user = plexUserRepository.findByPlexTvId(info.id())
                .or(() -> linkSyncedAccount(info))
                .orElseGet(() -> createOwnerIfFirstLogin(info));

        if (user.getRole() == Role.NONE) {
            throw new LoginDeniedException("'" + info.username() + "' (plexTvId=" + info.id() + ") has no access");
        }
        user.setEmail(info.email());
        user.setPlexThumb(info.thumb());
        user.setLastLoginAt(Instant.now().toString());
        // Only the owner's token is ever used (to query the Plex server): don't keep the others.
        if (user.isOwner()) user.setPlexTokenEncrypted(encryptor.encrypt(plexToken));
        return plexUserRepository.save(user);
    }

    // Links the plex.tv identity to its synced server account, but only once access is
    // granted: a denied attempt must leave no trace on the row.
    private Optional<PlexUser> linkSyncedAccount(PlexUserAccount info) {
        return plexUserService.findLoginCandidate(info.id())
                .filter(candidate -> candidate.getRole() != Role.NONE)
                .map(candidate -> {
                    candidate.setPlexTvId(info.id());
                    return candidate;
                });
    }

    private PlexUser createOwnerIfFirstLogin(PlexUserAccount info) {
        if (plexUserRepository.existsByOwnerTrue()) {
            throw new LoginDeniedException("'" + info.username() + "' (plexTvId=" + info.id()
                    + ") is unknown or has no access");
        }
        // plex_account_id (local server ID) will be linked on next user sync.
        return new PlexUser(info.id(), info.username(), info.email(), info.thumb(),
                null, Instant.now().toString());
    }

    private MeResponse toMeResponse(PlexUser user, int countedTotal) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isAdmin(), user.isOwner(), countedTotal);
    }
}
