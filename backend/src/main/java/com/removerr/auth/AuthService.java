package com.removerr.auth;

import com.removerr.auth.dto.*;
import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AuthService {

    private final PlexAuthClient plexClient;
    private final PlexUserRepository plexUserRepository;
    private final AesGcmEncryptor encryptor;
    private final JwtService jwtService;

    public AuthService(PlexAuthClient plexClient, PlexUserRepository plexUserRepository,
                       AesGcmEncryptor encryptor, JwtService jwtService) {
        this.plexClient = plexClient;
        this.plexUserRepository = plexUserRepository;
        this.encryptor = encryptor;
        this.jwtService = jwtService;
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
        PlexUser user = resolveAdmin(info, pin.authToken());
        String jwt = jwtService.generateToken(user);
        int countedTotal = (int) plexUserRepository.countByCounted(true);
        return PollResult.success(toMeResponse(user, countedTotal), jwt);
    }

    public MeResponse getMe(Long userId) {
        int countedTotal = (int) plexUserRepository.countByCounted(true);
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

    private PlexUser resolveAdmin(PlexUserAccount info, String plexToken) {
        String now = Instant.now().toString();
        String encryptedToken = encryptor.encrypt(plexToken);

        return plexUserRepository.findByPlexTvId(info.id())
                .map(existing -> {
                    // Admin re-login — update token and profile
                    existing.setPlexTokenEncrypted(encryptedToken);
                    existing.setLastLoginAt(now);
                    existing.setEmail(info.email());
                    existing.setPlexThumb(info.thumb());
                    return plexUserRepository.save(existing);
                })
                .orElseGet(() -> {
                    if (plexUserRepository.existsByAdminTrue()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "This Removerr instance already has an administrator");
                    }
                    // First login — create admin row with global Plex.tv ID.
                    // plex_account_id (local server ID) will be linked on next user sync.
                    return plexUserRepository.save(
                            new PlexUser(info.id(), info.username(), info.email(),
                                    info.thumb(), encryptedToken, now));
                });
    }

    private MeResponse toMeResponse(PlexUser user, int countedTotal) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.isAdmin(), countedTotal);
    }
}
