package com.removerr.setting;

import com.removerr.crypto.AesGcmEncryptor;
import com.removerr.setting.dto.SettingsResponse;
import com.removerr.setting.dto.UpdateSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppSettingService {

    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final AppSettingRepository repository;
    private final AesGcmEncryptor encryptor;

    // Settings are read before every call to Plex/Radarr/Sonarr/Seerr, including from the
    // library's parallel tasks: keeping them in memory stops those threads from competing for
    // the single SQLite connection. Values are stored as in the DB (encrypted keys included).
    private final Map<String, Optional<String>> storedValues = new ConcurrentHashMap<>();

    public AppSettingService(AppSettingRepository repository, AesGcmEncryptor encryptor) {
        this.repository = repository;
        this.encryptor = encryptor;
    }

    // Returns raw (decrypted) value, or null if not set
    public String get(String key) {
        String stored = stored(key);
        if (stored == null) return null;
        return isEncryptedKey(key) ? encryptor.decrypt(stored) : stored;
    }

    @Transactional
    public void set(String key, String value) {
        String stored = isEncryptedKey(key) ? encryptor.encrypt(value) : value;
        String now = Instant.now().toString();

        repository.findById(key).ifPresentOrElse(
                setting -> {
                    setting.setValue(stored);
                    setting.setUpdatedAt(now);
                },
                () -> repository.save(new AppSetting(key, stored, now))
        );
        storedValues.put(key, Optional.of(stored));
        forgetValuesIfRolledBack();
    }

    private void forgetValuesIfRolledBack() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storedValues.clear();
            }
        });
    }

    public SettingsResponse getSettings() {
        return new SettingsResponse(
                getRaw(AppSettingKey.PLEX_SERVER_URL),
                getRaw(AppSettingKey.RADARR_URL),
                isConfigured(AppSettingKey.RADARR_API_KEY),
                getRaw(AppSettingKey.SONARR_URL),
                isConfigured(AppSettingKey.SONARR_API_KEY),
                getRaw(AppSettingKey.SEERR_URL),
                isConfigured(AppSettingKey.SEERR_API_KEY),
                getRetentionDays()
        );
    }

    @Transactional
    public void updateSettings(UpdateSettingsRequest req) {
        setIfPresent(AppSettingKey.PLEX_SERVER_URL, req.plexServerUrl());
        setIfPresent(AppSettingKey.RADARR_URL, req.radarrUrl());
        setIfPresent(AppSettingKey.RADARR_API_KEY, req.radarrApiKey());
        setIfPresent(AppSettingKey.SONARR_URL, req.sonarrUrl());
        setIfPresent(AppSettingKey.SONARR_API_KEY, req.sonarrApiKey());
        setIfPresent(AppSettingKey.SEERR_URL, req.seerrUrl());
        setIfPresent(AppSettingKey.SEERR_API_KEY, req.seerrApiKey());
        if (req.trashRetentionDays() != null) {
            set(AppSettingKey.TRASH_RETENTION_DAYS, String.valueOf(req.trashRetentionDays()));
        }
    }

    public int getRetentionDays() {
        String value = getRaw(AppSettingKey.TRASH_RETENTION_DAYS);
        if (value == null) return DEFAULT_RETENTION_DAYS;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_RETENTION_DAYS;
        }
    }

    private String getRaw(String key) {
        return stored(key);
    }

    private boolean isConfigured(String key) {
        String stored = stored(key);
        return stored != null && !stored.isBlank();
    }

    private String stored(String key) {
        return storedValues.computeIfAbsent(key, k -> repository.findById(k).map(AppSetting::getValue))
                .orElse(null);
    }

    private void setIfPresent(String key, String value) {
        if (value != null) set(key, value);
    }

    private boolean isEncryptedKey(String key) {
        return AppSettingKey.ENCRYPTED.contains(key);
    }
}
