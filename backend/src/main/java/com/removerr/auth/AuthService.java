package com.removerr.auth;

import com.removerr.auth.dto.*;
import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.integration.plex.PlexLibraryClient;
import com.removerr.plexuser.PlexUser;
import com.removerr.plexuser.PlexUserRepository;
import com.removerr.plexuser.PlexUserService;
import com.removerr.plexuser.Role;
import com.removerr.plexuser.ServerAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final PlexAuthClient plexClient;
    private final PlexUserRepository plexUserRepository;
    private final AesGcmEncryptor encryptor;
    private final JwtService jwtService;
    private final PlexUserService plexUserService;
    private final TransactionTemplate txTemplate;
    private final PlexLibraryClient plexLibraryClient;

    // Plex PIN ids are sequential and polling only needs the id: the server-side nonce, also
    // held in the creating browser's cookie, is what proves the poll comes from that browser.
    // In memory on purpose: a pending login doesn't need to survive a restart.
    static final Duration PIN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<Long, PendingPin> pendingPins = new ConcurrentHashMap<>();

    public AuthService(PlexAuthClient plexClient, PlexUserRepository plexUserRepository,
                       AesGcmEncryptor encryptor, JwtService jwtService, PlexUserService plexUserService,
                       PlatformTransactionManager txManager, PlexLibraryClient plexLibraryClient) {
        this.plexClient = plexClient;
        this.plexUserRepository = plexUserRepository;
        this.encryptor = encryptor;
        this.jwtService = jwtService;
        this.plexUserService = plexUserService;
        this.txTemplate = new TransactionTemplate(txManager);
        this.plexLibraryClient = plexLibraryClient;
    }

    public record PinStart(CreatePinResponse response, String nonce) {}

    public PinStart createPin() {
        PlexPin pin = plexClient.createPin();
        forgetExpiredPins();
        String nonce = newNonce();
        pendingPins.put(pin.id(), new PendingPin(nonce, Instant.now().plus(PIN_TTL)));
        return new PinStart(new CreatePinResponse(pin.id(), pin.code(), plexClient.buildAuthUrl(pin.code())), nonce);
    }

    // Not @Transactional: polled every 2s during login, and a transaction would hold the single
    // SQLite connection across the plex.tv calls. Only the DB part runs in one.
    public PollResult pollPin(long pinId, String nonce) {
        // Unknown pin, wrong browser or expired: same answer, and plex.tv isn't even asked, so the
        // endpoint reveals nothing about which (sequential) pin ids belong to this instance.
        PendingPin pending = pendingPins.get(pinId);
        if (pending == null || !pending.matches(nonce)) return PollResult.expired();

        PlexPin pin = plexClient.checkPin(pinId);
        if (pin == null) {
            pendingPins.remove(pinId);
            return PollResult.expired();
        }
        if (pin.authToken() == null) return PollResult.pending();
        // Single use: a replayed or concurrent poll of an approved pin gets no second session.
        if (!pendingPins.remove(pinId, pending)) return PollResult.expired();

        PlexUserAccount info = plexClient.getUserInfo(pin.authToken());
        PollResult result = txTemplate.execute(status -> {
            PlexUser user = resolveUser(info, pin.authToken());
            String jwt = jwtService.generateToken(user);
            int countedTotal = (int) plexUserRepository.countByCountedTrueAndServerAccessNot(ServerAccess.NONE);
            return PollResult.success(toMeResponse(user, countedTotal), jwt);
        });
        // After commit, so a concurrent read can't cache the token being replaced.
        plexLibraryClient.forgetOwnerToken();
        return result;
    }

    private void forgetExpiredPins() {
        Instant now = Instant.now();
        pendingPins.values().removeIf(p -> p.expiresAt().isBefore(now));
    }

    private static String newNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record PendingPin(String nonce, Instant expiresAt) {
        boolean matches(String candidate) {
            return candidate != null && Instant.now().isBefore(expiresAt)
                    && MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                                             candidate.getBytes(StandardCharsets.UTF_8));
        }
    }

    public MeResponse getMe(Long userId) {
        int countedTotal = (int) plexUserRepository.countByCountedTrueAndServerAccessNot(ServerAccess.NONE);
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
