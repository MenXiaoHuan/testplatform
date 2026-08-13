package com.example.platform.ai.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.platform.ai.*;
import com.example.platform.ai.dto.ChatRequest;
import com.example.platform.ai.dto.ChatResponse;
import com.example.platform.ai.output.ChatAssistantResult;
import com.example.platform.ai.output.OutputFormatFallbackService;
import com.example.platform.ai.session.*;
import com.example.platform.ai.tools.ToolErrorFallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ReactAgent intelligentAssistantAgent;
    private final ChatSessionManager sessionManager;
    private final ContextCompressionService compressionService;
    private final OutputFormatFallbackService outputFallbackService;
    private final ToolErrorFallback toolErrorFallback;
    private final AgentCallManager callManager;
    private final InputSanitizer inputSanitizer;
    private final AgentObservability observability;

    public AgentService(
            @Qualifier("intelligent-assistant") ReactAgent intelligentAssistantAgent,
            ChatSessionManager sessionManager,
            ContextCompressionService compressionService,
            OutputFormatFallbackService outputFallbackService,
            ToolErrorFallback toolErrorFallback,
            AgentCallManager callManager,
            InputSanitizer inputSanitizer,
            AgentObservability observability) {
        this.intelligentAssistantAgent = intelligentAssistantAgent;
        this.sessionManager = sessionManager;
        this.compressionService = compressionService;
        this.outputFallbackService = outputFallbackService;
        this.toolErrorFallback = toolErrorFallback;
        this.callManager = callManager;
        this.inputSanitizer = inputSanitizer;
        this.observability = observability;
    }

    public ChatResponse chat(ChatRequest request) {
        Instant startTime = Instant.now();
        String sessionId = request.sessionId();

        log.info("Processing chat request: sessionId={}, spaceId={}, taskId={}, messageLength={}",
                sessionId, request.spaceId(), request.taskId(),
                request.message() != null ? request.message().length() : 0);

        observability.recordCall(sessionId);
        observability.recordConversationStart(sessionId, request.message());

        try {
            InputSanitizer.SanitizeResult sanitizeResult = inputSanitizer.sanitize(request.message());
            if (!sanitizeResult.valid()) {
                log.warn("Input sanitization failed: sessionId={}, reason={}",
                        sessionId, sanitizeResult.rejectionReason());
                observability.recordError(sessionId, "SANITIZATION", sanitizeResult.rejectionReason());
                return buildErrorResponse(
                        new IllegalArgumentException(sanitizeResult.rejectionReason()),
                        sessionId, false
                );
            }

            ChatSession session = sessionManager.getOrCreateSession(sessionId);

            ContextCompressionService.CompressionResult compressionResult =
                    compressionService.compressIfNeeded(session);
            if (compressionResult.compressed()) {
                session = compressionResult.session();
                sessionManager.updateMessages(sessionId, session.messages());
                log.info("Session context compressed: sessionId={}, tokens={}->{}",
                        sessionId, compressionResult.originalTokens(), compressionResult.compressedTokens());
            }

            ChatMessage userMessage = ChatMessage.user(sanitizeResult.sanitizedInput());
            session = sessionManager.appendMessage(sessionId, userMessage);

            String prompt = buildPrompt(session, request);

            AgentCallManager.CallResult<String> callResult = callManager.executeWithRetry(
                    () -> {
                        try {
                            return intelligentAssistantAgent.call(prompt).getText();
                        } catch (Exception e) {
                            throw new RuntimeException("Agent调用失败: " + e.getMessage(), e);
                        }
                    }
            );

            if (!callResult.success()) {
                log.error("Agent call failed: sessionId={}, error={}", sessionId, callResult.errorMessage());
                observability.recordError(sessionId, "AGENT_CALL", callResult.errorMessage());
                return buildErrorResponse(
                        new RuntimeException(callResult.errorMessage()),
                        sessionId,
                        compressionResult.compressed()
                );
            }

            String responseText = callResult.data();

            OutputFormatFallbackService.ParseResult parseResult =
                    outputFallbackService.parseAgentOutput(responseText);

            ChatAssistantResult result = parseResult.result();

            if (!parseResult.success()) {
                log.warn("Output parsing used fallback: sessionId={}, strategy={}",
                        sessionId, parseResult.strategy());
            }

            if (result.response() == null || result.response().isBlank()) {
                result = new ChatAssistantResult(responseText, result.usedTools(), result.confidence());
            }

            ChatMessage assistantMessage = ChatMessage.assistant(result.response());
            session = sessionManager.appendMessage(sessionId, assistantMessage);

            if (!request.saveHistory()) {
                log.debug("History not saved per request: sessionId={}", sessionId);
            }

            ToolErrorFallback.ToolCallAnalysis toolAnalysis =
                    toolErrorFallback.analyzeToolUsage(sessionId, result.usedTools());
            if (toolAnalysis.hasIssues()) {
                log.warn("Tool usage issues: sessionId={}, guidance={}", sessionId, toolAnalysis.guidance());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            String processingTime = elapsed.toMillis() + "ms";

            log.info("Chat processed: sessionId={}, responseLength={}, time={}, parsingStrategy={}, compressed={}",
                    sessionId,
                    result.response() != null ? result.response().length() : 0,
                    processingTime,
                    parseResult.strategy(),
                    compressionResult.compressed());

            observability.recordConversationEnd(sessionId, elapsed,
                    session.messageCount(), false);

            return new ChatResponse(
                    result.response(),
                    result.usedTools(),
                    result.confidence(),
                    request.taskId(),
                    request.sceneId(),
                    processingTime,
                    sessionId,
                    compressionResult.compressed()
            );

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("Unexpected error in chat: sessionId={}, time={}", sessionId, elapsed.toMillis(), e);
            observability.recordError(sessionId, "UNEXPECTED", e.getMessage());
            return buildErrorResponse(e, sessionId, false);
        }
    }

    public void clearSession(String sessionId) {
        sessionManager.clearSession(sessionId);
        log.info("Session cleared: sessionId={}", sessionId);
    }

    public ChatResponse chatStream(ChatRequest request, StreamCallback callback) {
        Instant startTime = Instant.now();
        String sessionId = request.sessionId();

        log.info("Processing streaming chat request: sessionId={}, spaceId={}", sessionId, request.spaceId());

        try {
            InputSanitizer.SanitizeResult sanitizeResult = inputSanitizer.sanitize(request.message());
            if (!sanitizeResult.valid()) {
                callback.onError(sanitizeResult.rejectionReason());
                return buildErrorResponse(
                        new IllegalArgumentException(sanitizeResult.rejectionReason()),
                        sessionId, false
                );
            }

            ChatSession session = sessionManager.getOrCreateSession(sessionId);
            ContextCompressionService.CompressionResult compressionResult =
                    compressionService.compressIfNeeded(session);

            ChatMessage userMessage = ChatMessage.user(sanitizeResult.sanitizedInput());
            session = sessionManager.appendMessage(sessionId, userMessage);

            String prompt = buildPrompt(session, request);

            AgentCallManager.CallResult<String> callResult = callManager.executeWithRetry(
                    () -> {
                        try {
                            return intelligentAssistantAgent.call(prompt).getText();
                        } catch (Exception e) {
                            throw new RuntimeException("Agent调用失败: " + e.getMessage(), e);
                        }
                    }
            );

            if (!callResult.success()) {
                callback.onError(callResult.errorMessage());
                return buildErrorResponse(
                        new RuntimeException(callResult.errorMessage()),
                        sessionId, compressionResult.compressed()
                );
            }

            String responseText = callResult.data();
            OutputFormatFallbackService.ParseResult parseResult =
                    outputFallbackService.parseAgentOutput(responseText);
            ChatAssistantResult result = parseResult.result();
            if (result.response() == null || result.response().isBlank()) {
                result = new ChatAssistantResult(responseText, result.usedTools(), result.confidence());
            }

            String fullResponse = result.response();
            int totalChars = fullResponse.length();
            int chunkSize = Math.max(1, totalChars / 80);
            int sent = 0;

            callback.onMeta(result.usedTools(), result.confidence());

            while (sent < totalChars) {
                int end = Math.min(sent + chunkSize, totalChars);
                String chunk = fullResponse.substring(sent, end);
                callback.onChunk(chunk);
                sent = end;
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            ChatMessage assistantMessage = ChatMessage.assistant(fullResponse);
            session = sessionManager.appendMessage(sessionId, assistantMessage);

            ToolErrorFallback.ToolCallAnalysis toolAnalysis =
                    toolErrorFallback.analyzeToolUsage(sessionId, result.usedTools());
            if (toolAnalysis.hasIssues()) {
                log.warn("Tool usage issues: sessionId={}, guidance={}", sessionId, toolAnalysis.guidance());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            String processingTime = elapsed.toMillis() + "ms";

            callback.onComplete(processingTime, sessionId);

            return new ChatResponse(
                    fullResponse,
                    result.usedTools(),
                    result.confidence(),
                    request.taskId(),
                    request.sceneId(),
                    processingTime,
                    sessionId,
                    compressionResult.compressed()
            );

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("Unexpected error in streaming chat: sessionId={}, time={}", sessionId, elapsed.toMillis(), e);
            callback.onError(e.getMessage());
            return buildErrorResponse(e, sessionId, false);
        }
    }

    public interface StreamCallback {
        void onMeta(List<String> usedTools, String confidence);
        void onChunk(String chunk);
        void onComplete(String processingTime, String sessionId);
        void onError(String error);
    }

    public long getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    private String buildPrompt(ChatSession session, ChatRequest request) {
        StringBuilder promptBuilder = new StringBuilder();

        if (session.systemPrompt() != null && !session.systemPrompt().isBlank()) {
            promptBuilder.append("System: ").append(session.systemPrompt()).append("\n\n");
        }

        appendContextInfo(promptBuilder, request);

        if (!session.messages().isEmpty()) {
            promptBuilder.append("\n\n--- 对话历史 ---\n");
            for (ChatMessage msg : session.messages()) {
                switch (msg.role()) {
                    case "user" -> promptBuilder.append("用户: ").append(msg.content()).append("\n");
                    case "assistant" -> promptBuilder.append("助手: ").append(msg.content()).append("\n");
                    case "tool" -> promptBuilder.append("[").append(msg.toolName()).append("]: ")
                            .append(msg.content() != null ? truncate(msg.content(), 200) : "")
                            .append("\n");
                }
            }
            promptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        return promptBuilder.toString();
    }

    private void appendContextInfo(StringBuilder builder, ChatRequest request) {
        List<String> contextParts = new ArrayList<>();

        if (request.spaceId() != null) {
            contextParts.add("所属空间ID: " + request.spaceId());
        }
        if (request.taskId() != null) {
            contextParts.add("关联任务ID: " + request.taskId());
        }
        if (request.sceneId() != null) {
            contextParts.add("关联场景ID: " + request.sceneId());
        }

        if (!contextParts.isEmpty()) {
            builder.append("[上下文信息] ").append(String.join(", ", contextParts)).append("\n");
        }

        if (request.spaceId() != null) {
            builder.append("【重要】数据隔离规则：你所有的工具调用都必须使用 spaceId=")
                    .append(request.spaceId())
                    .append(" 作为参数。禁止查询或分析其他空间的数据。所有 TaskTool、SceneTool、RepositoryTool、LogPreprocessingTool 的调用都必须传入此 spaceId。\n");
        }

        if (request.taskId() != null) {
            builder.append("【提示】用户正在询问任务 ID=").append(request.taskId())
                    .append(" 的相关信息。如果需要分析错误，请优先使用 analyzeLogs 工具并传入 taskId 和 spaceId。\n");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private ChatResponse buildErrorResponse(Exception e, String sessionId, boolean contextCompressed) {
        if (e instanceof IllegalArgumentException) {
            return new ChatResponse(
                    "请求参数错误: " + e.getMessage(),
                    List.of(),
                    "LOW",
                    null,
                    null,
                    "0ms",
                    sessionId,
                    contextCompressed
            );
        }
        log.error("Unexpected error in chat", e);
        return new ChatResponse(
                "抱歉，智能助手遇到了问题: " + e.getMessage() + "。请稍后重试。",
                List.of(),
                "LOW",
                null,
                null,
                "0ms",
                sessionId,
                contextCompressed
        );
    }
}