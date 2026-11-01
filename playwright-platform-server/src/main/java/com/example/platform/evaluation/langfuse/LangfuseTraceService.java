package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.exception.TargetInvokeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TargetAdapter 调用前后自动包装 Langfuse Trace 埋点（异步写入 Observation，
 * 保证主链路延迟不受 Langfuse 影响 —— C5）。
 */
@Component
public class LangfuseTraceService {

    private static final Logger log = LoggerFactory.getLogger(LangfuseTraceService.class);

    private final LangfuseClient langfuse;

    public LangfuseTraceService(LangfuseClient langfuse) {
        this.langfuse = langfuse;
    }

    /**
     * 同步包装：先写 Trace 头部 → 执行原始调用 → 成功后异步写 Observation；
     * 失败时写带 success=false 的 Observation，然后原样抛出异常。
     */
    public <T> T wrap(Supplier<T> originalCall, String traceName,
                      List<String> tags, Map<String, Object> metadata,
                      String input, String expectedModel,
                      Runnable onCaptureOutput) throws TargetInvokeException {

        String traceId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        // 1. 同步写 Trace header（极轻量）
        langfuse.writeTrace(TraceWriteRequest.builder()
                .traceId(traceId).name(traceName)
                .tags(tags).metadata(metadata)
                .timestamp(startTime)
                .build());

        try {
            // 2. 执行原始调用
            T result = originalCall.get();

            // 3. 异步写 Observation（不阻塞主链路）
            Instant endTime = Instant.now();
            langfuse.writeObservation(ObservationWriteRequest.builder()
                    .traceId(traceId).observationId(UUID.randomUUID().toString())
                    .name(traceName).input(input).model(expectedModel)
                    .startTime(startTime).endTime(endTime)
                    .success(true)
                    .build());

            // 通知调用方可以捕获 output 写 Observation（调用 onCaptureOutput 时 output 还没出来，改成回调方式）
            return result;
        } catch (Exception e) {
            // 失败也写 Observation，标记 success=false
            Instant endTime = Instant.now();
            langfuse.writeObservation(ObservationWriteRequest.builder()
                    .traceId(traceId).observationId(UUID.randomUUID().toString())
                    .name(traceName).input(input)
                    .startTime(startTime).endTime(endTime)
                    .success(false).level("ERROR")
                    .build());
            throw (e instanceof TargetInvokeException te)
                    ? te
                    : new TargetInvokeException("Target invoke failed: " + e.getMessage(), e);
        }
    }

    /** 写 Score（供评分器和手动评分共用）。 */
    public void writeScore(String traceId, String name, Double value, String comment) {
        langfuse.writeScore(ScoreWriteRequest.builder()
                .traceId(traceId).name(name).value(value).comment(comment)
                .build());
    }

    @FunctionalInterface
    public interface Supplier<T> { T get() throws TargetInvokeException; }
}
