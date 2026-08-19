package com.example.platform.ai.controller;

import com.example.platform.ai.AgentTraceLogService;
import com.example.platform.ai.dto.ChatRequest;
import com.example.platform.ai.dto.ChatResponse;
import com.example.platform.ai.service.AgentService;
import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.space.service.SpaceAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 对话控制器 —— 对外暴露 AI 对话相关的 HTTP 与 SSE 接口。
 *
 * <p>接口分组：
 * <ul>
 *   <li>{@code POST /api/ai/chat} —— 同步对话（一次性返回完整响应）</li>
 *   <li>{@code POST /api/ai/chat/stream} —— SSE 流式对话（meta → chunk → complete/error）</li>
 *   <li>{@code DELETE /api/ai/session/{id}} —— 清空会话</li>
 *   <li>{@code POST /api/ai/session/{id}/terminate} —— 主动终止会话（标记后端停止发送 chunk）</li>
 *   <li>{@code GET /api/ai/session/{id}/status} —— 查询会话终止状态</li>
 *   <li>{@code GET /api/ai/sessions/count} —— 当前活跃会话数</li>
 *   <li>{@code GET /api/ai/trace} / {@code /api/ai/trace/{traceId}} —— trace 链路查询</li>
 * </ul>
 *
 * <p>SSE 使用虚拟线程池执行，超时 300s。
 */
@RestController
@RequestMapping("/api/ai")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;
    private final AgentTraceLogService traceLogService;
    private final SpaceAuthorizationService spaceAuthorizationService;
    private final ExecutorService executorService;

    public AgentController(AgentService agentService, AgentTraceLogService traceLogService, SpaceAuthorizationService spaceAuthorizationService) {
        this.agentService = agentService;
        this.traceLogService = traceLogService;
        this.spaceAuthorizationService = spaceAuthorizationService;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: sessionId={}, messageLength={}, taskId={}, sceneId={}, spaceId={}",
                request.sessionId(),
                request.message() != null ? request.message().length() : 0,
                request.taskId(), request.sceneId(), request.spaceId());
        try {
            ChatResponse response = agentService.chat(request);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Failed to process chat request", e);
            return ApiResponse.error("CHAT_ERROR", e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // 在主线程中先完成空间成员校验（ThreadLocal 在线程池异步线程中会丢失）
        try {
            Long spaceId = request.spaceId();
            if (spaceId != null) {
                spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
            }
        } catch (Exception e) {
            log.warn("Space authorization check failed: spaceId={}, error={}", request.spaceId(), e.getMessage());
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", e.getMessage())));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
            return emitter;
        }

        // 保存当前 AuthContext 供异步线程使用
        AuthContext authContext = AuthContextHolder.get();

        executorService.execute(() -> {
            try {
                // 在异步线程中恢复 AuthContext
                if (authContext != null) {
                    AuthContextHolder.set(authContext);
                }

                agentService.chatStream(request, new AgentService.StreamCallback() {
                    @Override
                    public void onMeta(String traceId, java.util.List<String> usedTools, String confidence, String responseType, java.util.List<com.example.platform.ai.output.ContentBlock> sections) {
                        try {
                            List<Map<String, Object>> sectionMaps = sections != null
                                    ? sections.stream().map(s -> {
                                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                                        m.put("type", s.type());
                                        if (s.level() != null) m.put("level", s.level());
                                        if (s.text() != null) m.put("text", s.text());
                                        if (s.items() != null) m.put("items", s.items());
                                        if (s.ordered() != null) m.put("ordered", s.ordered());
                                        if (s.language() != null) m.put("language", s.language());
                                        if (s.code() != null) m.put("code", s.code());
                                        if (s.headers() != null) m.put("headers", s.headers());
                                        if (s.rows() != null) m.put("rows", s.rows());
                                        return m;
                                    }).toList()
                                    : List.of();
                            emitter.send(SseEmitter.event()
                                    .name("meta")
                                    .data(Map.of(
                                            "traceId", traceId != null ? traceId : "",
                                            "usedTools", usedTools,
                                            "confidence", confidence != null ? confidence : "",
                                            "responseType", responseType != null ? responseType : "UNKNOWN",
                                            "sections", sectionMaps
                                    )));
                        } catch (Exception e) {
                            log.error("[TRACE:{}] Failed to send meta event", traceId, e);
                        }
                    }

                    @Override
                    public void onChunk(String chunk) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("chunk")
                                    .data(chunk, MediaType.TEXT_PLAIN));
                        } catch (Exception e) {
                            log.error("Failed to send chunk event", e);
                        }
                    }

                    @Override
                    public void onComplete(String traceId, String processingTime, String sessionId) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(Map.of(
                                            "traceId", traceId != null ? traceId : "",
                                            "processingTime", processingTime,
                                            "sessionId", sessionId
                                    )));
                        } catch (Exception e) {
                            log.warn("[TRACE:{}] Failed to send complete event", traceId, e);
                        }
                        emitter.complete();
                    }

                    @Override
                    public void onError(String error) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(Map.of("error", error)));
                        } catch (Exception ex) {
                            log.warn("Failed to send error event", ex);
                        }
                        emitter.completeWithError(new RuntimeException(error));
                    }
                });
            } catch (Exception e) {
                log.error("Stream chat error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", e.getMessage())));
                } catch (Exception ex) {
                    log.warn("Failed to send error event", ex);
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.info("SSE stream completed for sessionId={}", request.sessionId()));
        emitter.onTimeout(() -> {
            log.warn("SSE stream timeout for sessionId={}", request.sessionId());
            emitter.complete();
        });
        emitter.onError(t -> log.error("SSE stream error for sessionId={}", request.sessionId(), t));

        return emitter;
    }

    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        agentService.clearSession(sessionId);
        return ApiResponse.ok(Map.of(
                "sessionId", sessionId,
                "status", "cleared"
        ));
    }

    @PostMapping("/session/{sessionId}/terminate")
    public ApiResponse<Map<String, Object>> terminateSession(@PathVariable String sessionId) {
        agentService.terminateSession(sessionId);
        return ApiResponse.ok(Map.of(
                "sessionId", sessionId,
                "status", "terminated"
        ));
    }

    @GetMapping("/session/{sessionId}/status")
    public ApiResponse<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        boolean terminated = agentService.isSessionTerminated(sessionId);
        return ApiResponse.ok(Map.of(
                "sessionId", sessionId,
                "terminated", terminated
        ));
    }

    @GetMapping("/sessions/count")
    public ApiResponse<Map<String, Object>> getActiveSessionCount() {
        long count = agentService.getActiveSessionCount();
        return ApiResponse.ok(Map.of("activeSessions", count));
    }

    /** 返回最近的 trace 摘要列表（按时间倒序），用于前端调度事件模块里的 Agent 调度事件列表。 */
    @GetMapping("/trace")
    public ApiResponse<List<AgentTraceLogService.TraceSummary>> listRecentTraces(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return ApiResponse.ok(traceLogService.queryRecentTraces(safeLimit));
    }

    /** 返回某条 trace 的完整时间线（所有日志条目按时间升序）。 */
    @GetMapping("/trace/{traceId}")
    public ApiResponse<List<AgentTraceLogService.TraceLogEntry>> getTrace(@PathVariable String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return ApiResponse.error("BAD_REQUEST", "traceId is required");
        }
        List<AgentTraceLogService.TraceLogEntry> entries = traceLogService.queryByTraceId(traceId);
        if (entries.isEmpty()) {
            return ApiResponse.error("NOT_FOUND", "trace not found: " + traceId);
        }
        return ApiResponse.ok(entries);
    }
}