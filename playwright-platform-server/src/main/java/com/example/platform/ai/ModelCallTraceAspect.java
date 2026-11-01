package com.example.platform.ai;

import com.example.platform.ai.trace.AgentTraceRecorder;
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
 * 模型调用链路切面 —— 拦截 {@code ChatModel.call(Prompt)} 调用，写入 Langfuse Observation。
 *
 * <p>记录内容：模型类名、消息角色列表、完整 prompt、实际 prompt/completion token 数、生成文本、
 * 工具调用信息与调用耗时。traceId 来自 {@link AgentTraceContext}。
 *
 * <p>token 用量同步给 {@link AgentObservability} 做会话级统计。
 */
@Aspect
@Component
public class ModelCallTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(ModelCallTraceAspect.class);

    private final AgentTraceRecorder traceRecorder;
    private final AgentObservability observability;

    public ModelCallTraceAspect(AgentTraceRecorder traceRecorder, AgentObservability observability) {
        this.traceRecorder = traceRecorder;
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

        String modelName = joinPoint.getTarget().getClass().getSimpleName();
        String promptFull = buildPromptFullText(prompt.getInstructions());
        int estimatedInputTokens = estimatePromptTokens(prompt);

        Map<String, Object> requestMeta = new LinkedHashMap<>();
        requestMeta.put("model", modelName);
        requestMeta.put("messageCount", prompt.getInstructions().size());
        requestMeta.put("messageRoles", extractRoles(prompt.getInstructions()));
        requestMeta.put("estimatedInputTokens", estimatedInputTokens);

        Instant start = Instant.now();
        String observationId = traceRecorder.newObservationId();
        AgentTraceContext.pushObservation(observationId);
        try {
            ChatResponse response = (ChatResponse) joinPoint.proceed();
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> responseMeta = new LinkedHashMap<>();
            responseMeta.put("model", modelName);
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
            if (!toolCallsJson.isEmpty()) {
                responseMeta.put("toolCalls", toolCallsJson);
                responseMeta.put("toolCallCount", countToolCalls(toolCallsJson));
            }
            responseMeta.put("tokenDiffVsEstimate", totalTokens - estimatedInputTokens);

            // 工具调用轮次的生成文本通常为空，此时输出 tool_calls JSON，避免观测节点输出为空
            String observedOutput = generationText;
            if (!toolCallsJson.isEmpty()) {
                observedOutput = (generationText.isBlank() ? "" : generationText + "\n\n")
                        + "tool_calls: " + toolCallsJson;
            }

            // 先出栈再写观测，保证 parentObservationId 指向调用方观测而非自身
            AgentTraceContext.popObservation();
            traceRecorder.recordSpan(traceId, "llm.call", promptFull,
                    observedOutput, start, Instant.now(), true, null,
                    (long) promptTokens, (long) completionTokens, modelName, responseMeta);

            if (promptTokens > 0 || completionTokens > 0) {
                observability.recordTokenUsage(traceId, promptTokens, completionTokens);
            }

            return response;
        } catch (Throwable t) {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            Map<String, Object> errorMeta = new LinkedHashMap<>();
            errorMeta.put("model", modelName);
            errorMeta.put("durationMs", durationMs);
            errorMeta.put("errorType", t.getClass().getSimpleName());
            errorMeta.put("errorMessage", t.getMessage());
            errorMeta.put("estimatedInputTokens", estimatedInputTokens);

            AgentTraceContext.popObservation();
            traceRecorder.recordSpan(traceId, "llm.call", promptFull,
                    t.getMessage(), start, Instant.now(), false, "ERROR",
                    null, null, modelName, errorMeta);

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
