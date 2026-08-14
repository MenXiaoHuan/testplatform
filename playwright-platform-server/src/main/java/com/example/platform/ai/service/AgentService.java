package com.example.platform.ai.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.platform.ai.*;
import com.example.platform.ai.config.SystemPromptConfig;
import com.example.platform.ai.dto.ChatRequest;
import com.example.platform.ai.dto.ChatResponse;
import com.example.platform.ai.output.ChatAssistantResult;
import com.example.platform.ai.output.OutputFormatFallbackService;
import com.example.platform.ai.session.*;
import com.example.platform.ai.skill.SkillIndexLoader;
import com.example.platform.ai.tools.ToolErrorFallback;
import com.example.platform.scene.service.ScheduleEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
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
    private final AgentTraceLogService traceLogService;
    private final SkillIndexLoader skillIndexLoader;
    private final ScheduleEventService scheduleEventService;
    private final String systemPrompt;

    public AgentService(
            @Qualifier("intelligent-assistant") ReactAgent intelligentAssistantAgent,
            ChatSessionManager sessionManager,
            ContextCompressionService compressionService,
            OutputFormatFallbackService outputFallbackService,
            ToolErrorFallback toolErrorFallback,
            AgentCallManager callManager,
            InputSanitizer inputSanitizer,
            AgentObservability observability,
            AgentTraceLogService traceLogService,
            SkillIndexLoader skillIndexLoader,
            ScheduleEventService scheduleEventService,
            SystemPromptConfig systemPromptConfig,
            ResourceLoader resourceLoader,
            @Value("${platform.ai.system-prompt-path:classpath:AGENT.md}") String systemPromptPath) {
        this.intelligentAssistantAgent = intelligentAssistantAgent;
        this.sessionManager = sessionManager;
        this.compressionService = compressionService;
        this.outputFallbackService = outputFallbackService;
        this.toolErrorFallback = toolErrorFallback;
        this.callManager = callManager;
        this.inputSanitizer = inputSanitizer;
        this.observability = observability;
        this.traceLogService = traceLogService;
        this.skillIndexLoader = skillIndexLoader;
        this.scheduleEventService = scheduleEventService;
        this.systemPrompt = systemPromptConfig.loadSystemPrompt(resourceLoader, systemPromptPath);
    }

    @PostConstruct
    public void validateSystemPrompt() {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            log.error("CRITICAL: System prompt is null or blank! Agent will operate without system prompt.");
        } else {
            int tokens = ChatSession.estimateTextTokens(systemPrompt);
            log.info("System prompt validated: length={} chars, estimatedTokens={}, preview={}",
                    systemPrompt.length(), tokens, truncate(systemPrompt, 200));
        }
    }

    public ChatResponse chat(ChatRequest request) {
        Instant startTime = Instant.now();
        String sessionId = request.sessionId();
        String traceId = UUID.randomUUID().toString();

        String userMessage = request.message() != null ? request.message() : "";

        log.info("[TRACE:{}] Processing chat request: sessionId={}, spaceId={}, taskId={}, messageLength={}",
                traceId, sessionId, request.spaceId(), request.taskId(), userMessage.length());

        traceLogService.log(traceId, "INFO", "REQUEST_RECEIVED",
                "Chat request received", Map.of(
                        "sessionId", sessionId != null ? sessionId : "new",
                        "spaceId", request.spaceId() != null ? request.spaceId() : 0,
                        "taskId", request.taskId() != null ? request.taskId() : "none",
                        "sceneId", request.sceneId() != null ? request.sceneId() : "none",
                        "messageLength", userMessage.length(),
                        "userMessage", userMessage,
                        "saveHistory", request.saveHistory()
                ));

        observability.recordCall(traceId, sessionId);
        observability.recordConversationStart(traceId, sessionId, request.message());

        Long agentEventId = scheduleEventService.createAgentEvent(
                request.spaceId(), traceId, sessionId, userMessage);
        final boolean[] success = {false};

        try {
            InputSanitizer.SanitizeResult sanitizeResult = inputSanitizer.sanitize(request.message());
            if (!sanitizeResult.valid()) {
                log.warn("[TRACE:{}] Input sanitization failed: sessionId={}, reason={}",
                        traceId, sessionId, sanitizeResult.rejectionReason());
                traceLogService.log(traceId, "ERROR", "SANITIZATION_FAILED",
                        "Input sanitization failed: " + sanitizeResult.rejectionReason(),
                        Map.of("sessionId", sessionId != null ? sessionId : "new"));
                observability.recordError(traceId, "SANITIZATION", sanitizeResult.rejectionReason());
                return buildErrorResponse(
                        traceId,
                        new IllegalArgumentException(sanitizeResult.rejectionReason()),
                        sessionId, false
                );
            }

            ChatSession session = sessionManager.getOrCreateSession(sessionId);
            session = ensureSystemPrompt(session, sessionId);

            log.info("System prompt check: sessionId={}, hasPrompt={}, promptLength={}, systemPromptTokens={}, estimatedTokens={}",
                    sessionId,
                    session.systemPrompt() != null && !session.systemPrompt().isBlank(),
                    session.systemPrompt() != null ? session.systemPrompt().length() : 0,
                    session.systemPromptTokens(),
                    session.estimatedTokens());

            Map<String, Object> sessionReadyMeta = new LinkedHashMap<>();
            sessionReadyMeta.put("sessionId", session.sessionId());
            sessionReadyMeta.put("messageCount", session.messageCount());
            sessionReadyMeta.put("estimatedTokens", session.estimatedTokens());
            sessionReadyMeta.put("hasSystemPrompt", session.systemPrompt() != null && !session.systemPrompt().isBlank());
            sessionReadyMeta.put("systemPromptTokens", session.systemPromptTokens());
            sessionReadyMeta.put("messageHistory", formatMessagesForTrace(session.messages()));
            traceLogService.log(traceId, "INFO", "SESSION_READY",
                    "Chat session loaded", sessionReadyMeta);

            ContextCompressionService.CompressionResult compressionResult =
                    compressionService.compressIfNeeded(session);
            if (compressionResult.compressed()) {
                session = compressionResult.session();
                sessionManager.updateMessages(sessionId, session.messages());
                log.info("Session context compressed: sessionId={}, tokens={}->{}",
                        sessionId, compressionResult.originalTokens(), compressionResult.compressedTokens());
                traceLogService.log(traceId, "INFO", "CONTEXT_COMPRESSED",
                        "Session context compressed",
                        Map.of("originalTokens", compressionResult.originalTokens(),
                                "compressedTokens", compressionResult.compressedTokens(),
                                "messageCount", session.messageCount(),
                                "systemPromptTokens", session.systemPromptTokens()));
            } else {
                Map<String, Object> ctxMeta = new LinkedHashMap<>();
                ctxMeta.put("messageCount", session.messageCount());
                ctxMeta.put("estimatedTokens", session.estimatedTokens());
                ctxMeta.put("compressed", false);
                ctxMeta.put("systemPromptTokens", session.systemPromptTokens());
                ctxMeta.put("sessionId", session.sessionId());
                ctxMeta.put("messageHistory", formatMessagesForTrace(session.messages()));
                traceLogService.log(traceId, "INFO", "CONTEXT_READY",
                        "Session context ready (not compressed)", ctxMeta);
            }

            Map<String, Object> systemPromptMeta = new LinkedHashMap<>();
            systemPromptMeta.put("systemPromptLength", session.systemPrompt() != null ? session.systemPrompt().length() : 0);
            systemPromptMeta.put("systemPrompt", session.systemPrompt() != null ? session.systemPrompt() : "");
            systemPromptMeta.put("systemPromptTokens", session.systemPromptTokens());
            systemPromptMeta.put("contextMessageCount", session.messageCount());
            systemPromptMeta.put("sessionId", session.sessionId());
            systemPromptMeta.put("totalEstimatedTokens", session.estimatedTokens());
            systemPromptMeta.put("messageTokens", session.messageTokens());
            if (skillIndexLoader != null) {
                List<String> skillNames = skillIndexLoader.getSkillNames();
                systemPromptMeta.put("skillCount", skillNames.size());
                systemPromptMeta.put("skillNames", skillNames);
                systemPromptMeta.put("skillIndexText", skillIndexLoader.getIndexText());
            }

            traceLogService.log(traceId, "INFO", "SYSTEM_PROMPT_LOADED",
                    "System prompt + skill index prepared", systemPromptMeta);

            ChatMessage chatUserMessage = ChatMessage.user(sanitizeResult.sanitizedInput());
            session = sessionManager.appendMessage(sessionId, chatUserMessage);

            int promptTokens = ChatSession.estimateTotalTokens(session.messages(), session.systemPrompt());
            int maxAllowedTokens = compressionService.getMaxTokens();

            traceLogService.log(traceId, "INFO", "PROMPT_TOKEN_BUDGET",
                    "Token budget check before Agent call",
                    Map.of(
                            "promptTokens", promptTokens,
                            "maxAllowedTokens", maxAllowedTokens,
                            "headroomTokens", Math.max(0, maxAllowedTokens - promptTokens),
                            "budgetUsagePercent", promptTokens > 0 ? Math.round(promptTokens * 100.0 / maxAllowedTokens) : 0
                    ));

            if (promptTokens > maxAllowedTokens) {
                log.warn("[TRACE:{}] Prompt tokens {} exceed max {}, applying aggressive truncation",
                        traceId, promptTokens, maxAllowedTokens);
                session = compressionService.compressIfNeeded(session).session();
                session = compressionService.truncateLongMessages(session);
                sessionManager.updateMessages(sessionId, session.messages());

                promptTokens = ChatSession.estimateTotalTokens(session.messages(), session.systemPrompt());
                traceLogService.log(traceId, "WARN", "PROMPT_TRUNCATED",
                        "Prompt truncated to fit token budget",
                        Map.of("truncatedTokens", promptTokens, "maxTokens", maxAllowedTokens));

                if (promptTokens > maxAllowedTokens) {
                    log.error("[TRACE:{}] Even after truncation, prompt tokens {} still exceed max {}",
                            traceId, promptTokens, maxAllowedTokens);
                    return buildErrorResponse(
                            traceId,
                            new RuntimeException("上下文过长，无法在 token 限制内处理。请开启新对话或简化问题。"),
                            sessionId, true
                    );
                }
            }

            String prompt = buildPrompt(session, request);

            Map<String, Object> promptBuiltMeta = new LinkedHashMap<>();
            promptBuiltMeta.put("promptLength", prompt.length());
            promptBuiltMeta.put("prompt", prompt);
            promptBuiltMeta.put("historyMessageCount", session.messageCount());
            promptBuiltMeta.put("currentUserMessage", sanitizeResult.sanitizedInput());
            promptBuiltMeta.put("sessionId", session.sessionId());
            promptBuiltMeta.put("estimatedTokens", session.estimatedTokens());
            promptBuiltMeta.put("systemPromptTokens", session.systemPromptTokens());
            promptBuiltMeta.put("messageTokens", session.messageTokens());
            promptBuiltMeta.put("hasSummary", hasStructuredSummary(session));
            promptBuiltMeta.put("structuredSummary", extractStructuredSummaryText(session));
            promptBuiltMeta.put("recentMessages", extractRecentMessagesText(session));
            traceLogService.log(traceId, "INFO", "PROMPT_BUILT",
                    "Full prompt constructed for Agent call", promptBuiltMeta);

            traceLogService.log(traceId, "INFO", "AGENT_CALL_STARTING",
                    "Starting Agent call with ReAct loop",
                    Map.of(
                            "model", "deepseek-chat",
                            "maxRetries", callManager.getMaxRetries(),
                            "timeoutSeconds", callManager.getTimeoutSeconds(),
                            "toolCount", 5,
                            "skillNames", skillIndexLoader != null
                                    ? String.join(",", skillIndexLoader.getSkillNames()) : "",
                            "loopLimit", 20,
                            "spaceId", request.spaceId() != null ? request.spaceId() : 0,
                            "taskId", request.taskId() != null ? request.taskId() : "none",
                            "sceneId", request.sceneId() != null ? request.sceneId() : "none"
                    ));

            AgentTraceContext.setTraceId(traceId);
            AgentCallManager.CallResult<String> callResult;
            try {
                callResult = callManager.executeWithRetry(
                        () -> {
                            try {
                                return intelligentAssistantAgent.call(prompt).getText();
                            } catch (Exception e) {
                                throw new RuntimeException("Agent调用失败: " + e.getMessage(), e);
                            }
                        }
                );
            } finally {
                AgentTraceContext.clear();
            }

            if (!callResult.success()) {
                log.error("[TRACE:{}] Agent call failed: sessionId={}, error={}", traceId, sessionId, callResult.errorMessage());
                traceLogService.log(traceId, "ERROR", "AGENT_CALL_FAILED",
                        "Agent call failed: " + callResult.errorMessage());
                observability.recordError(traceId, "AGENT_CALL", callResult.errorMessage());
                return buildErrorResponse(
                        traceId,
                        new RuntimeException(callResult.errorMessage()),
                        sessionId,
                        compressionResult.compressed()
                );
            }

            String responseText = callResult.data();

            traceLogService.log(traceId, "INFO", "AGENT_CALL_SUCCESS",
                    "Agent call completed successfully",
                    Map.of(
                            "responseLength", responseText != null ? responseText.length() : 0,
                            "agentOutput", responseText != null ? responseText : "",
                            "callDurationMs", callResult.durationMs()
                    ));

            OutputFormatFallbackService.ParseResult parseResult =
                    outputFallbackService.parseAgentOutput(responseText);

            ChatAssistantResult result = parseResult.result();

            if (!parseResult.success()) {
                log.warn("[TRACE:{}] Output parsing used fallback: sessionId={}, strategy={}",
                        traceId, sessionId, parseResult.strategy());
                traceLogService.log(traceId, "WARN", "OUTPUT_PARSE_FALLBACK",
                        "Output parsing used fallback: " + parseResult.strategy());
            }

            traceLogService.log(traceId, "INFO", "OUTPUT_PARSED",
                    "Agent output parsed",
                    Map.of(
                            "parsingStrategy", parseResult.strategy(),
                            "responseType", result.responseType() != null ? result.responseType() : "UNKNOWN",
                            "usedTools", result.usedTools() != null ? String.join(",", result.usedTools()) : "",
                            "usedToolsCount", result.usedTools() != null ? result.usedTools().size() : 0,
                            "confidence", result.confidence() != null ? result.confidence() : "UNKNOWN",
                            "sectionCount", result.sections() != null ? result.sections().size() : 0,
                            "response", result.deriveResponse()
                    ));

            ChatMessage assistantMessage = ChatMessage.assistant(result.deriveResponse());
            session = sessionManager.appendMessage(sessionId, assistantMessage);

            if (!request.saveHistory()) {
                log.debug("[TRACE:{}] History not saved per request: sessionId={}", traceId, sessionId);
            }

            ToolErrorFallback.ToolCallAnalysis toolAnalysis =
                    toolErrorFallback.analyzeToolUsage(traceId, sessionId, result.usedTools());
            if (toolAnalysis.hasIssues()) {
                log.warn("[TRACE:{}] Tool usage issues: sessionId={}, guidance={}", traceId, sessionId, toolAnalysis.guidance());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            String processingTime = formatDuration(elapsed);

            String responseText = result.deriveResponse();
            log.info("[TRACE:{}] Chat processed: sessionId={}, responseLength={}, time={}, parsingStrategy={}, responseType={}, compressed={}",
                    traceId,
                    sessionId,
                    responseText.length(),
                    processingTime,
                    parseResult.strategy(),
                    result.responseType(),
                    compressionResult.compressed());

            traceLogService.log(traceId, "INFO", "REQUEST_COMPLETED",
                    "Chat request completed",
                    Map.of("responseLength", responseText.length(),
                            "processingTime", processingTime,
                            "parsingStrategy", parseResult.strategy(),
                            "responseType", result.responseType() != null ? result.responseType() : "UNKNOWN",
                            "compressed", compressionResult.compressed(),
                            "usedTools", result.usedTools() != null ? String.join(",", result.usedTools()) : ""));

            observability.recordConversationEnd(traceId, sessionId, elapsed,
                    session.messageCount(), false);

            success[0] = true;
            return new ChatResponse(
                    traceId,
                    responseText,
                    result.usedTools(),
                    result.confidence(),
                    result.responseType(),
                    faultDetailToMap(result.faultDetail()),
                    request.taskId(),
                    request.sceneId(),
                    processingTime,
                    sessionId,
                    compressionResult.compressed()
            );

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("[TRACE:{}] Unexpected error in chat: sessionId={}, time={}", traceId, sessionId, elapsed.toMillis(), e);
            observability.recordError(traceId, "UNEXPECTED", e.getMessage());
            return buildErrorResponse(traceId, e, sessionId, false);
        } finally {
            scheduleEventService.completeAgentEvent(agentEventId, success[0], success[0] ? null : "chat failed");
        }
    }

    public void clearSession(String sessionId) {
        sessionManager.clearSession(sessionId);
        log.info("Session cleared: sessionId={}", sessionId);
    }

    public ChatResponse chatStream(ChatRequest request, StreamCallback callback) {
        Instant startTime = Instant.now();
        String sessionId = request.sessionId();
        String traceId = UUID.randomUUID().toString();

        String userMessage = request.message() != null ? request.message() : "";

        log.info("[TRACE:{}] Processing streaming chat request: sessionId={}, spaceId={}, taskId={}, messageLength={}",
                traceId, sessionId, request.spaceId(), request.taskId(), userMessage.length());

        traceLogService.log(traceId, "INFO", "REQUEST_RECEIVED",
                "Streaming chat request received", Map.of(
                        "sessionId", sessionId != null ? sessionId : "new",
                        "spaceId", request.spaceId() != null ? request.spaceId() : 0,
                        "taskId", request.taskId() != null ? request.taskId() : "none",
                        "sceneId", request.sceneId() != null ? request.sceneId() : "none",
                        "messageLength", userMessage.length(),
                        "userMessage", userMessage,
                        "saveHistory", request.saveHistory()
                ));

        observability.recordCall(traceId, sessionId);
        observability.recordConversationStart(traceId, sessionId, request.message());

        Long agentEventId = scheduleEventService.createAgentEvent(
                request.spaceId(), traceId, sessionId, userMessage);
        final boolean[] success = {false};

        try {
            InputSanitizer.SanitizeResult sanitizeResult = inputSanitizer.sanitize(request.message());
            if (!sanitizeResult.valid()) {
                log.warn("[TRACE:{}] Input sanitization failed: sessionId={}, reason={}",
                        traceId, sessionId, sanitizeResult.rejectionReason());
                traceLogService.log(traceId, "ERROR", "SANITIZATION_FAILED",
                        "Input sanitization failed: " + sanitizeResult.rejectionReason(),
                        Map.of("sessionId", sessionId != null ? sessionId : "new"));
                observability.recordError(traceId, "SANITIZATION", sanitizeResult.rejectionReason());
                callback.onError(sanitizeResult.rejectionReason());
                return buildErrorResponse(
                        traceId,
                        new IllegalArgumentException(sanitizeResult.rejectionReason()),
                        sessionId, false
                );
            }

            ChatSession session = sessionManager.getOrCreateSession(sessionId);
            session = ensureSystemPrompt(session, sessionId);

            log.info("System prompt check (stream): sessionId={}, hasPrompt={}, promptLength={}, systemPromptTokens={}, estimatedTokens={}",
                    sessionId,
                    session.systemPrompt() != null && !session.systemPrompt().isBlank(),
                    session.systemPrompt() != null ? session.systemPrompt().length() : 0,
                    session.systemPromptTokens(),
                    session.estimatedTokens());

            Map<String, Object> sessionReadyMeta = new LinkedHashMap<>();
            sessionReadyMeta.put("sessionId", session.sessionId());
            sessionReadyMeta.put("messageCount", session.messageCount());
            sessionReadyMeta.put("estimatedTokens", session.estimatedTokens());
            sessionReadyMeta.put("hasSystemPrompt", session.systemPrompt() != null && !session.systemPrompt().isBlank());
            sessionReadyMeta.put("systemPromptTokens", session.systemPromptTokens());
            sessionReadyMeta.put("messageHistory", formatMessagesForTrace(session.messages()));
            traceLogService.log(traceId, "INFO", "SESSION_READY",
                    "Chat session loaded (stream)", sessionReadyMeta);

            ContextCompressionService.CompressionResult compressionResult =
                    compressionService.compressIfNeeded(session);

            if (compressionResult.compressed()) {
                session = compressionResult.session();
                sessionManager.updateMessages(sessionId, session.messages());
                log.info("Session context compressed (stream): sessionId={}, tokens={}->{}",
                        sessionId, compressionResult.originalTokens(), compressionResult.compressedTokens());
                traceLogService.log(traceId, "INFO", "CONTEXT_COMPRESSED",
                        "Session context compressed (stream)",
                        Map.of("originalTokens", compressionResult.originalTokens(),
                                "compressedTokens", compressionResult.compressedTokens(),
                                "messageCount", session.messageCount(),
                                "systemPromptTokens", session.systemPromptTokens()));
            } else {
                Map<String, Object> ctxMeta = new LinkedHashMap<>();
                ctxMeta.put("messageCount", session.messageCount());
                ctxMeta.put("estimatedTokens", session.estimatedTokens());
                ctxMeta.put("compressed", false);
                ctxMeta.put("systemPromptTokens", session.systemPromptTokens());
                ctxMeta.put("sessionId", session.sessionId());
                ctxMeta.put("messageHistory", formatMessagesForTrace(session.messages()));
                traceLogService.log(traceId, "INFO", "CONTEXT_READY",
                        "Session context ready (stream, not compressed)", ctxMeta);
            }

            Map<String, Object> systemPromptMeta = new LinkedHashMap<>();
            systemPromptMeta.put("systemPromptLength", session.systemPrompt() != null ? session.systemPrompt().length() : 0);
            systemPromptMeta.put("systemPrompt", session.systemPrompt() != null ? session.systemPrompt() : "");
            systemPromptMeta.put("systemPromptTokens", session.systemPromptTokens());
            systemPromptMeta.put("contextMessageCount", session.messageCount());
            systemPromptMeta.put("sessionId", session.sessionId());
            systemPromptMeta.put("totalEstimatedTokens", session.estimatedTokens());
            systemPromptMeta.put("messageTokens", session.messageTokens());
            if (skillIndexLoader != null) {
                List<String> skillNames = skillIndexLoader.getSkillNames();
                systemPromptMeta.put("skillCount", skillNames.size());
                systemPromptMeta.put("skillNames", skillNames);
                systemPromptMeta.put("skillIndexText", skillIndexLoader.getIndexText());
            }

            traceLogService.log(traceId, "INFO", "SYSTEM_PROMPT_LOADED",
                    "System prompt + skill index prepared (stream)", systemPromptMeta);

            ChatMessage chatUserMsg = ChatMessage.user(sanitizeResult.sanitizedInput());
            session = sessionManager.appendMessage(sessionId, chatUserMsg);

            int promptTokens = ChatSession.estimateTotalTokens(session.messages(), session.systemPrompt());
            int maxAllowedTokens = compressionService.getMaxTokens();

            traceLogService.log(traceId, "INFO", "PROMPT_TOKEN_BUDGET",
                    "Token budget check before Agent call (stream)",
                    Map.of(
                            "promptTokens", promptTokens,
                            "maxAllowedTokens", maxAllowedTokens,
                            "headroomTokens", Math.max(0, maxAllowedTokens - promptTokens),
                            "budgetUsagePercent", promptTokens > 0 ? Math.round(promptTokens * 100.0 / maxAllowedTokens) : 0
                    ));

            if (promptTokens > maxAllowedTokens) {
                log.warn("[TRACE:{}] Prompt tokens {} exceed max {}, applying aggressive truncation (stream)",
                        traceId, promptTokens, maxAllowedTokens);
                session = compressionService.compressIfNeeded(session).session();
                session = compressionService.truncateLongMessages(session);
                sessionManager.updateMessages(sessionId, session.messages());
                promptTokens = ChatSession.estimateTotalTokens(session.messages(), session.systemPrompt());

                traceLogService.log(traceId, "WARN", "PROMPT_TRUNCATED",
                        "Prompt truncated to fit token budget (stream)",
                        Map.of("truncatedTokens", promptTokens, "maxTokens", maxAllowedTokens));

                if (promptTokens > maxAllowedTokens) {
                    log.error("[TRACE:{}] Even after truncation, prompt tokens {} still exceed max {} (stream)",
                            traceId, promptTokens, maxAllowedTokens);
                    callback.onError("上下文过长，无法在 token 限制内处理。请开启新对话或简化问题。");
                    return buildErrorResponse(
                            traceId,
                            new RuntimeException("上下文过长，无法在 token 限制内处理"),
                            sessionId, true
                    );
                }
            }

            String prompt = buildPrompt(session, request);

            Map<String, Object> promptBuiltMetaStream = new LinkedHashMap<>();
            promptBuiltMetaStream.put("promptLength", prompt.length());
            promptBuiltMetaStream.put("prompt", prompt);
            promptBuiltMetaStream.put("historyMessageCount", session.messageCount());
            promptBuiltMetaStream.put("currentUserMessage", sanitizeResult.sanitizedInput());
            promptBuiltMetaStream.put("sessionId", session.sessionId());
            promptBuiltMetaStream.put("estimatedTokens", session.estimatedTokens());
            promptBuiltMetaStream.put("systemPromptTokens", session.systemPromptTokens());
            promptBuiltMetaStream.put("messageTokens", session.messageTokens());
            promptBuiltMetaStream.put("hasSummary", hasStructuredSummary(session));
            promptBuiltMetaStream.put("structuredSummary", extractStructuredSummaryText(session));
            promptBuiltMetaStream.put("recentMessages", extractRecentMessagesText(session));
            traceLogService.log(traceId, "INFO", "PROMPT_BUILT",
                    "Full prompt constructed for Agent call (stream)", promptBuiltMetaStream);

            traceLogService.log(traceId, "INFO", "AGENT_CALL_STARTING",
                    "Starting Agent call with ReAct loop (stream)",
                    Map.of(
                            "model", "deepseek-chat",
                            "maxRetries", callManager.getMaxRetries(),
                            "timeoutSeconds", callManager.getTimeoutSeconds(),
                            "toolCount", 5,
                            "skillNames", skillIndexLoader != null
                                    ? String.join(",", skillIndexLoader.getSkillNames()) : "",
                            "loopLimit", 20,
                            "spaceId", request.spaceId() != null ? request.spaceId() : 0,
                            "taskId", request.taskId() != null ? request.taskId() : "none",
                            "sceneId", request.sceneId() != null ? request.sceneId() : "none"
                    ));

            AgentTraceContext.setTraceId(traceId);
            AgentCallManager.CallResult<String> callResult;
            try {
                callResult = callManager.executeWithRetry(
                        () -> {
                            try {
                                return intelligentAssistantAgent.call(prompt).getText();
                            } catch (Exception e) {
                                throw new RuntimeException("Agent调用失败: " + e.getMessage(), e);
                            }
                        }
                );
            } finally {
                AgentTraceContext.clear();
            }

            if (!callResult.success()) {
                log.error("[TRACE:{}] Agent call failed (stream): sessionId={}, error={}", traceId, sessionId, callResult.errorMessage());
                traceLogService.log(traceId, "ERROR", "AGENT_CALL_FAILED",
                        "Agent call failed (stream): " + callResult.errorMessage());
                observability.recordError(traceId, "AGENT_CALL", callResult.errorMessage());
                callback.onError(callResult.errorMessage());
                return buildErrorResponse(
                        traceId,
                        new RuntimeException(callResult.errorMessage()),
                        sessionId, compressionResult.compressed()
                );
            }

            String responseText = callResult.data();

            traceLogService.log(traceId, "INFO", "AGENT_CALL_SUCCESS",
                    "Agent call completed successfully (stream)",
                    Map.of(
                            "responseLength", responseText != null ? responseText.length() : 0,
                            "agentOutput", responseText != null ? responseText : "",
                            "callDurationMs", callResult.durationMs()
                    ));

            OutputFormatFallbackService.ParseResult parseResult =
                    outputFallbackService.parseAgentOutput(responseText);
            ChatAssistantResult result = parseResult.result();

            if (!parseResult.success()) {
                log.warn("[TRACE:{}] Output parsing used fallback (stream): sessionId={}, strategy={}",
                        traceId, sessionId, parseResult.strategy());
                traceLogService.log(traceId, "WARN", "OUTPUT_PARSE_FALLBACK",
                        "Output parsing used fallback (stream): " + parseResult.strategy());
            }

            String fullResponse = result.deriveResponse();

            traceLogService.log(traceId, "INFO", "OUTPUT_PARSED",
                    "Agent output parsed (stream)",
                    Map.of(
                            "parsingStrategy", parseResult.strategy(),
                            "responseType", result.responseType() != null ? result.responseType() : "UNKNOWN",
                            "usedTools", result.usedTools() != null ? String.join(",", result.usedTools()) : "",
                            "usedToolsCount", result.usedTools() != null ? result.usedTools().size() : 0,
                            "confidence", result.confidence() != null ? result.confidence() : "UNKNOWN",
                            "sectionCount", result.sections() != null ? result.sections().size() : 0
                    ));

            int totalChars = fullResponse.length();
            int chunkSize = Math.max(1, totalChars / 80);
            int sent = 0;

            callback.onMeta(traceId, result.usedTools(), result.confidence(), result.responseType(), result.sections());

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
                    toolErrorFallback.analyzeToolUsage(traceId, sessionId, result.usedTools());
            if (toolAnalysis.hasIssues()) {
                log.warn("[TRACE:{}] Tool usage issues (stream): sessionId={}, guidance={}", traceId, sessionId, toolAnalysis.guidance());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            String processingTime = formatDuration(elapsed);

            log.info("[TRACE:{}] Streaming chat processed: sessionId={}, responseLength={}, time={}, parsingStrategy={}, responseType={}, compressed={}",
                    traceId,
                    sessionId,
                    fullResponse.length(),
                    processingTime,
                    parseResult.strategy(),
                    result.responseType(),
                    compressionResult.compressed());

            callback.onComplete(traceId, processingTime, sessionId);

            traceLogService.log(traceId, "INFO", "REQUEST_COMPLETED",
                    "Streaming chat request completed",
                    Map.of(
                            "responseLength", fullResponse.length(),
                            "processingTime", processingTime,
                            "parsingStrategy", parseResult.strategy(),
                            "responseType", result.responseType() != null ? result.responseType() : "UNKNOWN",
                            "compressed", compressionResult.compressed(),
                            "usedTools", result.usedTools() != null ? String.join(",", result.usedTools()) : "",
                            "streamMode", "stream",
                            "sessionId", sessionId != null ? sessionId : "new",
                            "messageCount", session.messageCount(),
                            "hasSummary", hasStructuredSummary(session)
                    ));

            observability.recordConversationEnd(traceId, sessionId, elapsed,
                    session.messageCount(), false);

            success[0] = true;
            return new ChatResponse(
                    traceId,
                    fullResponse,
                    result.usedTools(),
                    result.confidence(),
                    result.responseType(),
                    faultDetailToMap(result.faultDetail()),
                    request.taskId(),
                    request.sceneId(),
                    processingTime,
                    sessionId,
                    compressionResult.compressed()
            );

        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("[TRACE:{}] Unexpected error in streaming chat: sessionId={}, time={}", traceId, sessionId, elapsed.toMillis(), e);
            traceLogService.log(traceId, "ERROR", "UNEXPECTED_ERROR",
                    "Unexpected error in streaming chat: " + e.getMessage());
            observability.recordError(traceId, "UNEXPECTED", e.getMessage());
            callback.onError(e.getMessage());
            return buildErrorResponse(traceId, e, sessionId, false);
        } finally {
            scheduleEventService.completeAgentEvent(agentEventId, success[0], success[0] ? null : "stream chat failed");
        }
    }

    public interface StreamCallback {
        void onMeta(String traceId, List<String> usedTools, String confidence, String responseType, List<com.example.platform.ai.output.ContentBlock> sections);
        void onChunk(String chunk);
        void onComplete(String traceId, String processingTime, String sessionId);
        void onError(String error);
    }

    public long getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    private String buildPrompt(ChatSession session, ChatRequest request) {
        StringBuilder promptBuilder = new StringBuilder();

        appendContextInfo(promptBuilder, request);

        if (!session.messages().isEmpty()) {
            List<ChatMessage> messages = session.messages();
            boolean hasSummary = false;
            int firstRecentIdx = 0;

            for (int i = 0; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                if (msg.isAssistant() && msg.content() != null
                        && (msg.content().startsWith("[历史对话摘要]") || msg.content().startsWith("[历史对话摘要·精简]"))) {
                    if (!hasSummary) {
                        promptBuilder.append("\n--- 历史对话摘要 ---\n");
                        hasSummary = true;
                    }
                    String summaryContent = msg.content()
                            .replace("[历史对话摘要]\n", "")
                            .replace("[历史对话摘要·精简]\n", "")
                            .replace("[历史对话摘要·LLM]\n", "");
                    promptBuilder.append(summaryContent).append("\n");
                    firstRecentIdx = i + 1;
                }
            }

            if (firstRecentIdx < messages.size()) {
                promptBuilder.append("\n--- 最近对话 ---\n");
                for (int i = firstRecentIdx; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    switch (msg.role()) {
                        case "user" -> promptBuilder.append("用户: ").append(msg.content()).append("\n");
                        case "assistant" -> promptBuilder.append("助手: ").append(msg.content()).append("\n");
                        case "tool" -> promptBuilder.append("[").append(msg.toolName()).append("]: ")
                                .append(msg.content() != null ? truncate(msg.content(), 200) : "")
                                .append("\n");
                    }
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
        if (request.sceneId() != null && request.taskId() == null) {
            builder.append("【提示】用户正在询问场景 ID=").append(request.sceneId())
                    .append(" 的相关信息。如果需要分析该场景下的任务，请使用 listTasks(sceneId=").append(request.sceneId())
                    .append(", spaceId=").append(request.spaceId())
                    .append(")。\n");
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
            builder.append("【重要】用户正在询问一个具体任务，任务 ID=").append(request.taskId())
                    .append("。你必须且只能分析这一个任务。")
                    .append("请立即调用 TaskTool.getTask(taskId=").append(request.taskId())
                    .append(", spaceId=").append(request.spaceId())
                    .append(") 获取任务详情，然后调用 LogPreprocessingTool.analyzeLogs(taskId=").append(request.taskId())
                    .append(", spaceId=").append(request.spaceId())
                    .append(") 分析该任务的错误日志。")
                    .append("禁止调用 listTasks 或 listScenes 来查询整个空间的数据。\n");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private ChatResponse buildErrorResponse(String traceId, Exception e, String sessionId, boolean contextCompressed) {
        if (e instanceof IllegalArgumentException) {
            return new ChatResponse(
                    traceId,
                    "请求参数错误: " + e.getMessage(),
                    List.of(),
                    "LOW",
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    "0ms",
                    sessionId,
                    contextCompressed
            );
        }
        log.error("[TRACE:{}] Unexpected error in chat", traceId, e);
        return new ChatResponse(
                traceId,
                "抱歉，智能助手遇到了问题: " + e.getMessage() + "。请稍后重试。",
                List.of(),
                "LOW",
                "UNKNOWN",
                null,
                null,
                null,
                "0ms",
                sessionId,
                contextCompressed
        );
    }

    private Map<String, Object> faultDetailToMap(ChatAssistantResult.FaultDetail faultDetail) {
        if (faultDetail == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fault_type", faultDetail.fault_type());
        map.put("root_cause", faultDetail.root_cause());
        map.put("immediate_solution", faultDetail.immediate_solution());
        map.put("long_term_optimize", faultDetail.long_term_optimize());
        map.put("test_risk", faultDetail.test_risk());
        map.put("reproduce_steps", faultDetail.reproduce_steps());
        return map;
    }

    private ChatSession ensureSystemPrompt(ChatSession session, String sessionId) {
        if (session.systemPrompt() == null || session.systemPrompt().isBlank()) {
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ChatSession updated = session.withSystemPrompt(systemPrompt);
                sessionManager.updateSystemPrompt(sessionId, systemPrompt);
                log.info("System prompt set on session: sessionId={}, promptLength={}", sessionId, systemPrompt.length());
                return updated;
            }
        }
        return session;
    }

    private boolean hasStructuredSummary(ChatSession session) {
        if (session == null || session.messages() == null) {
            return false;
        }
        for (ChatMessage msg : session.messages()) {
            if (msg.isAssistant() && msg.content() != null
                    && (msg.content().startsWith("[历史对话摘要]")
                        || msg.content().startsWith("[历史对话摘要·精简]")
                        || msg.content().startsWith("[历史对话摘要·LLM]"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 session 中第一条「历史对话摘要」消息的完整内容（含 [历史对话摘要] 前缀）。
     * 没有摘要时返回空串。
     */
    private String extractStructuredSummaryText(ChatSession session) {
        if (session == null || session.messages() == null) {
            return "";
        }
        for (ChatMessage msg : session.messages()) {
            if (msg.isAssistant() && msg.content() != null
                    && (msg.content().startsWith("[历史对话摘要]")
                        || msg.content().startsWith("[历史对话摘要·精简]")
                        || msg.content().startsWith("[历史对话摘要·LLM]"))) {
                return msg.content();
            }
        }
        return "";
    }

    /**
     * 提取结构化摘要之后的所有「最近对话」原始文本，按 "用户: ... / 助手: ... / [toolName]: ..." 拼接。
     */
    private String extractRecentMessagesText(ChatSession session) {
        if (session == null || session.messages() == null || session.messages().isEmpty()) {
            return "";
        }
        List<ChatMessage> messages = session.messages();
        int firstRecentIdx = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.isAssistant() && msg.content() != null
                    && (msg.content().startsWith("[历史对话摘要]")
                        || msg.content().startsWith("[历史对话摘要·精简]")
                        || msg.content().startsWith("[历史对话摘要·LLM]"))) {
                firstRecentIdx = i + 1;
            }
        }
        if (firstRecentIdx >= messages.size()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = firstRecentIdx; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            switch (msg.role()) {
                case "user" -> sb.append("用户: ").append(msg.content()).append("\n");
                case "assistant" -> sb.append("助手: ").append(msg.content()).append("\n");
                case "tool" -> sb.append("[").append(msg.toolName()).append("]: ")
                        .append(msg.content() != null ? msg.content() : "")
                        .append("\n");
            }
        }
        return sb.toString();
    }


    private int countRecentMessages(ChatSession session) {
        if (session == null || session.messages() == null) {
            return 0;
        }
        int count = 0;
        boolean foundSummary = false;
        for (ChatMessage msg : session.messages()) {
            if (msg.isAssistant() && msg.content() != null
                    && (msg.content().startsWith("[历史对话摘要]")
                        || msg.content().startsWith("[历史对话摘要·精简]")
                        || msg.content().startsWith("[历史对话摘要·LLM]"))) {
                foundSummary = true;
                continue;
            }
            if (foundSummary) {
                count++;
            }
        }
        return count;
    }

    private String formatMessagesForTrace(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.role()).append("]");
            if (msg.toolName() != null) {
                sb.append("(").append(msg.toolName()).append(")");
            }
            sb.append(": ").append(msg.content() != null ? msg.content() : "").append("\n");
        }
        return sb.toString();
    }

    private static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + "ms";
        double seconds = ms / 1000.0;
        if (seconds < 60) return String.format("%.1fs", seconds);
        long min = (long) seconds / 60;
        double rem = seconds - min * 60;
        return String.format("%dm %.1fs", min, rem);
    }
}