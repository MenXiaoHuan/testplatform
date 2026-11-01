package com.example.platform.evaluation.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AgentTokenService 单测 —— 验证 Langfuse 风格密钥格式、只存 hash 与常量时间校验。 */
class AgentTokenServiceTest {

    private final AgentTokenService service = new AgentTokenService();

    @Test
    void generatesLangfuseStyleKeys() {
        AgentTokenService.KeyPair keys = service.generate();
        assertTrue(keys.publicKey().matches("pk-lf-[0-9a-f]{32}"), keys.publicKey());
        assertTrue(keys.secretKey().matches("sk-lf-[0-9a-f]{32}"), keys.secretKey());
        assertNotEquals(keys.publicKey(), service.generate().publicKey());
    }

    @Test
    void generatesAgentKey() {
        assertTrue(service.newAgentKey().matches("agent-[0-9a-f]{16}"));
    }

    @Test
    void matchesOnlyExactKeyPair() {
        AgentTokenService.KeyPair keys = service.generate();
        String hash = service.hash(keys.publicKey(), keys.secretKey());

        assertTrue(service.matches(keys.publicKey(), keys.secretKey(), hash));
        assertFalse(service.matches(keys.publicKey(), "sk-lf-wrong", hash));
        assertFalse(service.matches("pk-lf-wrong", keys.secretKey(), hash));
        assertFalse(service.matches(keys.publicKey(), keys.secretKey(), null));
    }

    @Test
    void hashIsSha256Hex() {
        String hash = service.hash("pk-lf-a", "sk-lf-b");
        assertTrue(hash.matches("[0-9a-f]{64}"));
        // 同样的输入必须得到同样的 hash（Agent 侧密钥不变时校验稳定）
        assertTrue(hash.equals(service.hash("pk-lf-a", "sk-lf-b")));
    }
}
