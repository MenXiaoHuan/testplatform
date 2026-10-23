package com.example.platform.common;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 仓库凭据的对称加密（AES-256-GCM）。
 *
 * <p>数据库只保存密文；API 永远不返回明文。密钥来自
 * {@code PLATFORM_CREDENTIAL_KEY}，必须是 32 字节的 base64。
 *
 * <p>如果密钥未配置，公有仓库仍可正常使用；保存私有仓库凭据时会明确失败，
 * 避免悄悄把 token 以明文写进数据库。
 */
@Component
public class CredentialCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public CredentialCipher(@Value("${platform.security.credential-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PLATFORM_CREDENTIAL_KEY 不是合法的 base64", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PLATFORM_CREDENTIAL_KEY 必须是 32 字节的 base64");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (key == null) {
            throw new IllegalStateException(
                    "未配置 PLATFORM_CREDENTIAL_KEY，无法保存私有仓库凭据");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("加密仓库凭据失败", exception);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        if (key == null) {
            throw new IllegalStateException(
                    "未配置 PLATFORM_CREDENTIAL_KEY，无法读取私有仓库凭据");
        }
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalStateException("仓库凭据格式不兼容，请重新保存凭据");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("解密仓库凭据失败，请检查 PLATFORM_CREDENTIAL_KEY 是否变更", exception);
        }
    }
}
