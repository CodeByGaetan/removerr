package com.removerr.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    public AesGcmEncryptor(@Value("${removerr.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "REMOVERR_ENC_KEY must decode to 16, 24, or 32 bytes (got " + keyBytes.length + ")");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    // Output format: base64(iv || ciphertext+tag)
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, output, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public String decrypt(String base64Ciphertext) {
        try {
            byte[] input = Base64.getDecoder().decode(base64Ciphertext);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[input.length - IV_LENGTH_BYTES];
            System.arraycopy(input, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(input, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
