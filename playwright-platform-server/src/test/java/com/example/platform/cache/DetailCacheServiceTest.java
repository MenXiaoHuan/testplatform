package com.example.platform.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;

class DetailCacheServiceTest {
    private final RedisTemplate<String, String> redisTemplate = Mockito.mock(RedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CacheProperties properties = new CacheProperties();
    private final Map<String, String> redisValues = new ConcurrentHashMap<>();

    @BeforeEach
    void setUpRedisTemplate() {
        redisValues.clear();
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.get(Mockito.anyString()))
                .thenAnswer(invocation -> redisValues.get(invocation.getArgument(0, String.class)));
        Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class));
        Mockito.when(valueOperations.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class)))
                .thenAnswer(invocation -> redisValues.putIfAbsent(
                        invocation.getArgument(0, String.class),
                        invocation.getArgument(1, String.class)) == null);
        Mockito.when(redisTemplate.delete(Mockito.anyString()))
                .thenAnswer(invocation -> redisValues.remove(invocation.getArgument(0, String.class)) != null);
    }

    @Test
    void shouldReturnCachedValueWhenNormalHit() throws Exception {
        DetailCacheService service = service();
        redisValues.put("detail:repository:1", objectMapper.writeValueAsString(CachedValue.hit(new DetailDto(1L, "repo"))));

        Optional<DetailDto> result = service.getOrLoad("repository", 1L, DetailDto.class,
                () -> Optional.of(new DetailDto(2L, "loader")));

        assertThat(result).contains(new DetailDto(1L, "repo"));
        Mockito.verify(valueOperations, Mockito.never()).setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class));
    }

    @Test
    void shouldCacheEmptyValueWhenLoaderReturnsEmpty() {
        DetailCacheService service = service();

        Optional<DetailDto> result = service.getOrLoad("scene", 404L, DetailDto.class, Optional::empty);

        assertThat(result).isEmpty();
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        Mockito.verify(valueOperations).set(Mockito.eq("detail:scene:404"), valueCaptor.capture(), ttlCaptor.capture());
        CachedValue cachedValue = objectMapper.convertValue(readJson(valueCaptor.getValue()), CachedValue.class);
        assertThat(cachedValue.empty()).isTrue();
        assertThat(ttlCaptor.getValue()).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(60));
        Mockito.verify(redisTemplate).delete("detail:scene:404:lock");
    }

    @Test
    void shouldApplyTtlJitterWhenCachingLoadedValue() {
        DetailCacheService service = service();

        Optional<DetailDto> result = service.getOrLoad("task", 7L, DetailDto.class,
                () -> Optional.of(new DetailDto(7L, "task")));

        assertThat(result).contains(new DetailDto(7L, "task"));
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        Mockito.verify(valueOperations).set(Mockito.eq("detail:task:7"), Mockito.anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(90));
    }

    @Test
    void shouldRunOnlyOneLoaderWhenConcurrentRequestsMissSameKey() throws Exception {
        DetailCacheService service = service();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Supplier<Optional<DetailDto>> loader = () -> {
            loads.incrementAndGet();
            await(start);
            return Optional.of(new DetailDto(8L, "repo"));
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);

        var first = executor.submit(() -> service.getOrLoad("repository", 8L, DetailDto.class, loader));
        var second = executor.submit(() -> service.getOrLoad("repository", 8L, DetailDto.class, loader));
        while (loads.get() == 0) {
            Thread.sleep(10);
        }
        start.countDown();

        assertThat(first.get(1, TimeUnit.SECONDS)).contains(new DetailDto(8L, "repo"));
        assertThat(second.get(1, TimeUnit.SECONDS)).contains(new DetailDto(8L, "repo"));
        assertThat(loads).hasValue(1);
        executor.shutdownNow();
    }

    @Test
    void shouldInvalidateCachedDetail() {
        DetailCacheService service = service();

        service.invalidate("repository", 12L);

        Mockito.verify(redisTemplate).delete("detail:repository:12");
    }

    private DetailCacheService service() {
        properties.setDetailTtl(Duration.ofSeconds(60));
        properties.setNullTtl(Duration.ofSeconds(30));
        properties.setJitterSeconds(30);
        properties.setMutexTtl(Duration.ofSeconds(5));
        return new DetailCacheService(redisTemplate, objectMapper, properties);
    }

    private Object readJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    record DetailDto(Long id, String name) {
    }
}
