package com.example.platform.ai;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型调用链路切面 —— 拦截 {@code ChatModel.call(Prompt)} 调用，记录开始/完成/失败三类日志。
 *
 * <p>记录内容：模型类名、消息角色列表、首条消息预览、估算输入 token 数、实际 prompt/completion token 数、
 * 生成文本预览、调用耗时。所有日志写入 {@link AgentTraceLogService}，traceId 来自 {@link AgentTraceContext}。
 *
 * <p>token 用量同步给 {@link AgentObservability} 做会话级统计。
 */
@Aspect
@Component
public class ModelCallTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(ModelCallTraceAspect.class);

    private final AgentTraceLogService traceLogService;
    private final AgentObservability observability;

    public ModelCallTraceAspect(AgentTraceLogService traceLogService, AgentObservability observability) {
        this.traceLogService = traceLogService;
        this.observability = observability;
    }

    @Around("execution(* org.springframework.ai.chat.model.ChatModel.call(org.springframework.ai.chat.prompt.Prompt))")
    public Object traceModelCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = AgentTraceContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        Prompt prompt = (Prompt) args[0];

        Map<String, Object> requestMeta = new LinkedHashMap<>();
        requestMeta.put("model", joinPoint.getTarget().getClass().getSimpleName());
        requestMeta.put("messageCount", prompt.getInstructions().size());
        requestMeta.put("messageRoles", extractRoles(prompt.getInstructions()));
        requestMeta.put("firstMessageFull", getFirstMessageText(prompt.getInstructions()));
        requestMeta.put("promptFull", buildPromptFullText(prompt.getInstructions()));
        int estimatedInputTokens = estimatePromptTokens(prompt);
        requestMeta.put("estimatedInputTokens", estimatedInputTokens);

        traceLogService.log(traceId, "INFO", "MODEL_CALL_STARTING",
                "LLM model call starting", requestMeta);

        Instant start = Instant.now();
        try {
            ChatResponse response = (ChatResponse) joinPoint.proceed();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> responseMeta = new LinkedHashMap<>();
            responseMeta.put("model", joinPoint.getTarget().getClass().getSimpleName());
            responseMeta.put("durationMs", durationMs);

            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            String generationText = "";
            String toolCallsJson = "";

            if (response != null) {
                if (response.getResults() != null && !response.getResults().isEmpty()) {
                    var generation = response.getResults().get(0);
                    generationText = generation.getOutput() != null ? generation.getOutput().getText() : "";
                    // 记录 tool_calls 信息
                    if (generation.getOutput() != null && generation.getOutput().getToolCalls() != null) {
                        var toolCalls = generation.getOutput().getToolCalls();
                        if (!toolCalls.isEmpty()) {
                            StringBuilder tcSb = new StringBuilder("[");
                            for (int i = 0; i < toolCalls.size(); i++) {
                                if (i > 0) tcSb.append(",");
                                var tc = toolCalls.get(i);
                                tcSb.append("{\"name\":\"").append(tc.name()).append("\"");
                                if (tc.arguments() != null) {
                                    tcSb.append(",\"arguments\":").append(tc.arguments());
                                }
                                tcSb.append("}");
                            }
                            tcSb.append("]");
                            toolCallsJson = tcSb.toString();
                        }
                    }
                }

                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    var usage = response.getMetadata().getUsage();
                    Integer pt = usage.getPromptTokens();
                    Integer ct = usage.getCompletionTokens();
                    Integer tt = usage.getTotalTokens();
                    promptTokens = pt != null ? pt : 0;
                    completionTokens = ct != null ? ct : 0;
                    totalTokens = tt != null ? tt : 0;
                }
            }

            responseMeta.put("promptTokens", promptTokens);
            responseMeta.put("completionTokens", completionTokens);
            responseMeta.put("totalTokens", totalTokens);
            responseMeta.put("generationLength", generationText.length());
            responseMeta.put("generationFull", generationText);
            if (!toolCallsJson.isEmpty()) {
                responseMeta.put("toolCalls", toolCallsJson);
                responseMeta.put("toolCallCount", countToolCalls(toolCallsJson));
            }
            responseMeta.put("estimatedInputTokens", estimatedInputTokens);
            responseMeta.put("tokenDiffVsEstimate", totalTokens - estimatedInputTokens);

            traceLogService.log(traceId, "INFO", "MODEL_CALL_COMPLETED",
                    "LLM model call completed in " + durationMs + "ms, tokens: " + totalTokens,
                    responseMeta);

            if (promptTokens > 0 || completionTokens > 0) {
                observability.recordTokenUsage(traceId, promptTokens, completionTokens);
            }

            return response;
        } catch (Throwable t) {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> errorMeta = new LinkedHashMap<>();
            errorMeta.put("model", joinPoint.getTarget().getClass().getSimpleName());
            errorMeta.put("durationMs", durationMs);
            errorMeta.put("errorType", t.getClass().getSimpleName());
            errorMeta.put("errorMessage", t.getMessage());
            errorMeta.put("estimatedInputTokens", estimatedInputTokens);

            traceLogService.log(traceId, "ERROR", "MODEL_CALL_FAILED",
                    "LLM model call failed: " + t.getMessage(), errorMeta);

            throw t;
        }
    }

    private String extractRoles(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messages.get(i).getMessageType());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 获取第一条消息的完整文本
     */
    private String getFirstMessageText(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "";
        String text = messages.get(0).getText();
        return text != null ? text : "";
    }

    /**
     * 构建完整的 prompt 文本，包含所有消息的角色和内容
     */
    private String buildPromptFullText(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            sb.append("[")
              .append(msg.getMessageType())
              .append("]\n")
              .append(msg.getText() != null ? msg.getText() : "")
              .append("\n---\n");
        }
        return sb.toString();
    }

    /**
     * 计算 tool_calls 数量
     */
    private int countToolCalls(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = toolCallsJson.indexOf("\"name\"", idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    private int estimatePromptTokens(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null) return 0;
        int total = 0;
        for (Message msg : prompt.getInstructions()) {
            total += estimateTextTokens(msg.getText());
        }
        return total;
    }

    private int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return Math.max(1, (int) (chineseChars * 1.5 + otherChars * 0.25));
    }
}
