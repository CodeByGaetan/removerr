package com.removerr.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY);

    @Test
    void encryptThenDecrypt_returnsOriginal() {
        String plaintext = "radarr-api-key-äöü-123";
        assertThat(encryptor.decrypt(encryptor.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void encrypt_usesAFreshIvEachTime() {
        // Same input must not yield the same ciphertext (random IV per call).
        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    void decrypt_rejectsTamperedCiphertext() {
        byte[] raw = Base64.getDecoder().decode(encryptor.encrypt("secret"));
        raw[raw.length - 1] ^= 0x01; // flip a bit in the GCM tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(AesGcmEncryptor.EncryptionException.class);
    }

    @Test
    void constructor_rejectsInvalidKeyLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[10]);
        assertThatThrownBy(() -> new AesGcmEncryptor(tooShort))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
