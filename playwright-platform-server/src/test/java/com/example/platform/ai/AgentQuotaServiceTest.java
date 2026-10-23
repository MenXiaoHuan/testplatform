package com.example.platform.ai;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentQuotaServiceTest {
    @Test
    void shouldRejectWhenDailyLimitExceeded() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(values);
        Mockito.when(values.increment(Mockito.anyString())).thenReturn(2L);

        AgentQuotaService service = new AgentQuotaService(redis, 1, 1);

        assertThatThrownBy(() -> service.acquire(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("今日 AI 调用次数已达上限");
    }

    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(values);
        Mockito.when(values.increment(Mockito.anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        AgentQuotaService service = new AgentQuotaService(redis, 10, 1);
        try (AgentQuotaService.Permit ignored = service.acquire(7L)) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void shouldReleaseConcurrencyPermit() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(values);
        Mockito.when(values.increment(Mockito.anyString())).thenReturn(1L);

        AgentQuotaService service = new AgentQuotaService(redis, 100, 1);
        AgentQuotaService.Permit first = service.acquire(7L);
        first.close();

        try (AgentQuotaService.Permit second = service.acquire(7L)) {
            assertThat(second).isNotNull();
        }
    }
}
