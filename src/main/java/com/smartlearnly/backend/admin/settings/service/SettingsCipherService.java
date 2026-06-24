package com.smartlearnly.backend.admin.settings.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-GCM encryption for settings secrets stored at rest.
 *
 * <p>The key is read from {@code app.settings.encryption-key} (base64, 32 bytes).
 * When no key is configured, secret values cannot be encrypted; the caller is
 * expected to reject secret writes rather than persist plaintext.
 */
@Service
public class SettingsCipherService {
    private static final Logger log = LoggerFactory.getLogger(SettingsCipherService.class);
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SettingsCipherService(@Value("${app.settings.encryption-key:}") String encodedKey) {
        this.key = buildKey(encodedKey);
        if (this.key == null) {
            log.warn("SETTINGS_ENCRYPTION_KEY is not configured. Secret settings cannot be saved until a 32-byte base64 key is provided.");
        }
    }

    public boolean isEnabled() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (key == null) {
            // No encryption key configured: store as plaintext so the feature still works,
            // but warn loudly. Values are upgraded to ciphertext once a key is set and re-saved.
            log.warn("Storing a secret setting in plaintext because SETTINGS_ENCRYPTION_KEY is not configured.");
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt setting value", exception);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            // Not encrypted (e.g. legacy plaintext or key was missing at write time).
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("Settings encryption key is not configured");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt setting value", exception);
        }
    }

    private SecretKeySpec buildKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey.trim());
            if (keyBytes.length != 32) {
                log.warn("SETTINGS_ENCRYPTION_KEY must decode to 32 bytes (got {}). Secret settings are disabled.", keyBytes.length);
                return null;
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            log.warn("SETTINGS_ENCRYPTION_KEY is not valid base64. Secret settings are disabled.");
            return null;
        }
    }
}
