package com.example.platform.evaluation.agent;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Agent 接入密钥生成与校验。
 *
 * <p>对外发放 Langfuse 风格的 public/secret key，Agent 的 Langfuse SDK 可以直接用；
 * 平台只保存 secret 的 SHA-256，不落明文。
 */
@Component
public class AgentTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record KeyPair(String publicKey, String secretKey) {}

    public KeyPair generate() {
        return new KeyPair("pk-lf-" + randomHex(16), "sk-lf-" + randomHex(16));
    }

    public String newAgentKey() {
        return "agent-" + randomHex(8);
    }

    public String hash(String publicKey, String secretKey) {
        return sha256Hex(publicKey + ":" + secretKey);
    }

    public boolean matches(String publicKey, String secretKey, String expectedHash) {
        if (expectedHash == null || publicKey == null || secretKey == null) {
            return false;
        }
        String actual = hash(publicKey, secretKey);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buffer) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
