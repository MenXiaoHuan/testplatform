package com.example.platform.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Agent 调用管理器 —— 统一封装 Agent/LLM 调用的超时控制、重试策略与线程池。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #executeWithTimeout(Supplier)} —— 单次调用，超时自动取消并返回失败结果</li>
 *   <li>{@link #executeWithRetry(Supplier)} —— 带指数退避重试，默认 2 次重试，间隔翻倍</li>
 *   <li>内部维护一个 4-8 核心线程、60s 空闲回收的线程池，{@link ThreadPoolExecutor.CallerRunsPolicy} 背压</li>
 *   <li>跨线程透传 {@link AgentTraceContext} 的 traceId，保证链路追踪不丢失</li>
 * </ul>
 *
 * <p>配置项（application.yml 中 platform.ai.call.*）：
 * <ul>
 *   <li>timeout-seconds 默认 60s</li>
 *   <li>max-retries 默认 2</li>
 *   <li>retry-delay-ms 默认 1000ms，按 attempt 指数增长</li>
 * </ul>
 */
@Component
public class AgentCallManager {

    private static final Logger log = LoggerFactory.getLogger(AgentCallManager.class);

    private final ExecutorService executorService;

    @Value("${platform.ai.call.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${platform.ai.call.max-retries:2}")
    private int maxRetries;

    @Value("${platform.ai.call.retry-delay-ms:1000}")
    private long retryDelayMs;

    public AgentCallManager() {
        this.executorService = new ThreadPoolExecutor(
                4,
                8,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public <T> CallResult<T> executeWithTimeout(Supplier<T> action) {
        return executeWithTimeout(action, timeoutSeconds);
    }

    public <T> CallResult<T> executeWithTimeout(Supplier<T> action, int timeoutSec) {
        String traceId = AgentTraceContext.getTraceId();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            String prev = AgentTraceContext.getTraceId();
            if (traceId != null) {
                AgentTraceContext.setTraceId(traceId);
            }
            try {
                return action.get();
            } finally {
                if (traceId != null) {
                    AgentTraceContext.clear();
                }
                if (prev != null) {
                    AgentTraceContext.setTraceId(prev);
                }
            }
        }, executorService);

        try {
            T result = future.get(timeoutSec, TimeUnit.SECONDS);
            return CallResult.success(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Agent call timed out after {} seconds", timeoutSec);
            return CallResult.failure("Agent响应超时(" + timeoutSec + "秒)，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Agent call interrupted", e);
            return CallResult.failure("请求被中断，请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Agent call failed with exception", cause);
            return CallResult.failure("Agent调用失败: " + cause.getMessage());
        }
    }

    public <T> CallResult<T> executeWithRetry(Supplier<T> action) {
        return executeWithRetry(action, maxRetries);
    }

    public <T> CallResult<T> executeWithRetry(Supplier<T> action, int retries) {
        Exception lastException = null;
        long totalDurationMs = 0;

        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) {
                long delay = retryDelayMs * (long) Math.pow(2, attempt - 1);
                log.info("Retrying agent call: attempt={}, delay={}ms", attempt + 1, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return CallResult.failure("请求被中断");
                }
                totalDurationMs += delay;
            }

            Instant startTime = Instant.now();

            try {
                CallResult<T> result = executeWithTimeout(action);
                Duration elapsed = Duration.between(startTime, Instant.now());
                totalDurationMs += elapsed.toMillis();
                if (result.success()) {
                    log.debug("Agent call succeeded: attempt={}, time={}ms, totalTime={}ms",
                            attempt + 1, elapsed.toMillis(), totalDurationMs);
                    return CallResult.success(result.data(), totalDurationMs);
                }
                lastException = new RuntimeException(result.errorMessage());
                log.warn("Agent call attempt {} failed: {}", attempt + 1, result.errorMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("Agent call attempt {} threw exception: {}", attempt + 1, e.getMessage());
            }
        }

        log.error("Agent call failed after {} attempts", retries + 1, lastException);
        return CallResult.failure("经过" + (retries + 1) + "次尝试后仍然失败: " + lastException.getMessage(), totalDurationMs);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record CallResult<T>(
            T data,
            boolean success,
            String errorMessage,
            long durationMs
    ) {
        public static <T> CallResult<T> success(T data) {
            return new CallResult<>(data, true, null, 0);
        }

        public static <T> CallResult<T> success(T data, long durationMs) {
            return new CallResult<>(data, true, null, durationMs);
        }

        public static <T> CallResult<T> failure(String errorMessage) {
            return new CallResult<>(null, false, errorMessage, 0);
        }

        public static <T> CallResult<T> failure(String errorMessage, long durationMs) {
            return new CallResult<>(null, false, errorMessage, durationMs);
        }

        public long durationMs() {
            return durationMs;
        }
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}