package com.example.platform.ai.controller;

import com.example.platform.ai.dto.ChatRequest;
import com.example.platform.ai.dto.ChatResponse;
import com.example.platform.ai.service.AgentService;
import com.example.platform.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;
    private final ExecutorService executorService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
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

        executorService.execute(() -> {
            try {
                agentService.chatStream(request, new AgentService.StreamCallback() {
                    @Override
                    public void onMeta(String traceId, java.util.List<String> usedTools, String confidence, String responseType) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("meta")
                                    .data(Map.of(
                                            "traceId", traceId != null ? traceId : "",
                                            "usedTools", usedTools,
                                            "confidence", confidence != null ? confidence : "",
                                            "responseType", responseType != null ? responseType : "UNKNOWN"
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
                                    .data(chunk));
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

    @GetMapping("/sessions/count")
    public ApiResponse<Map<String, Object>> getActiveSessionCount() {
        long count = agentService.getActiveSessionCount();
        return ApiResponse.ok(Map.of("activeSessions", count));
    }
}