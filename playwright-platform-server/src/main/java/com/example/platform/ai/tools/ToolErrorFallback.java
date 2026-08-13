package com.example.platform.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ToolErrorFallback {

    private static final Logger log = LoggerFactory.getLogger(ToolErrorFallback.class);

    private static final Set<String> KNOWN_TOOLS = Set.of(
            "repositoryTool", "sceneTool", "taskTool", "logPreprocessingTool",
            "getRepository", "listScenes", "getTask", "getTaskLogs",
            "searchRepository", "getSceneDetail", "listTasks", "analyzeLogs"
    );

    private final Map<String, AtomicInteger> toolFailureCounts = new ConcurrentHashMap<>();
    private final Map<String, String> toolErrorPatterns = new ConcurrentHashMap<>();

    public ToolCallAnalysis analyzeToolUsage(String sessionId, List<String> usedTools) {
        return analyzeToolUsage(null, sessionId, usedTools);
    }

    public ToolCallAnalysis analyzeToolUsage(String traceId, String sessionId, List<String> usedTools) {
        if (usedTools == null || usedTools.isEmpty()) {
            return ToolCallAnalysis.noTools();
        }

        Map<String, Long> toolCounts = usedTools.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        tool -> tool,
                        java.util.stream.Collectors.counting()
                ));

        List<String> unknownTools = new ArrayList<>();
        List<String> overusedTools = new ArrayList<>();

        for (Map.Entry<String, Long> entry : toolCounts.entrySet()) {
            String tool = entry.getKey();
            Long count = entry.getValue();

            if (!KNOWN_TOOLS.contains(tool)) {
                unknownTools.add(tool);
            }

            if (count >= 5) {
                overusedTools.add(tool);
            }
        }

        boolean hasIssues = !unknownTools.isEmpty() || !overusedTools.isEmpty();

        if (hasIssues) {
            log.warn("[TRACE:{}] Tool usage issues detected: sessionId={}, unknownTools={}, overusedTools={}",
                    traceId, sessionId, unknownTools, overusedTools);
        }

        return new ToolCallAnalysis(
                toolCounts,
                unknownTools,
                overusedTools,
                hasIssues,
                generateGuidance(unknownTools, overusedTools)
        );
    }

    public String getToolErrorFeedback(String toolName, String errorMessage) {
        if (toolName == null || errorMessage == null) {
            return null;
        }

        String key = toolName + ":" + errorMessage;
        int failureCount = toolFailureCounts.computeIfAbsent(toolName, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (failureCount >= 3) {
            log.warn("Tool {} has failed {} times, providing guidance", toolName, failureCount);
            return buildGuidanceFeedback(toolName, errorMessage, failureCount);
        }

        return buildFriendlyFeedback(toolName, errorMessage);
    }

    public void resetFailureCount(String toolName) {
        if (toolName != null) {
            toolFailureCounts.remove(toolName);
        }
    }

    public void resetAllFailures() {
        toolFailureCounts.clear();
    }

    private String buildFriendlyFeedback(String toolName, String errorMessage) {
        return switch (toolName) {
            case "taskTool", "getTask", "listTasks" ->
                    "任务查询失败: " + sanitizeError(errorMessage) +
                    "。请确认任务ID是否正确，或者尝试查询其他任务。";
            case "sceneTool", "getSceneDetail", "listScenes" ->
                    "场景查询失败: " + sanitizeError(errorMessage) +
                    "。请确认场景ID是否正确，或者尝试列出所有场景。";
            case "repositoryTool", "getRepository", "searchRepository" ->
                    "仓库查询失败: " + sanitizeError(errorMessage) +
                    "。请确认仓库名称或ID是否正确。";
            case "logPreprocessingTool", "getTaskLogs", "analyzeLogs" ->
                    "日志分析失败: " + sanitizeError(errorMessage) +
                    "。该任务可能还没有执行日志，或者日志已过期。";
            default ->
                    "工具调用失败: " + sanitizeError(errorMessage) +
                    "。请尝试使用其他工具或换一种方式提问。";
        };
    }

    private String buildGuidanceFeedback(String toolName, String errorMessage, int failureCount) {
        return "工具[" + toolName + "]已连续失败" + failureCount + "次。" +
                "错误信息: " + sanitizeError(errorMessage) +
                "。建议: 1) 检查参数是否正确 2) 换用其他可用工具 3) 直接基于已有信息回答用户";
    }

    private String sanitizeError(String errorMessage) {
        if (errorMessage == null) return "未知错误";
        if (errorMessage.length() > 200) {
            return errorMessage.substring(0, 200) + "...";
        }
        return errorMessage.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String generateGuidance(List<String> unknownTools, List<String> overusedTools) {
        StringBuilder sb = new StringBuilder();

        if (!unknownTools.isEmpty()) {
            sb.append("Agent使用了未注册的工具: ").append(unknownTools).append("。");
        }

        if (!overusedTools.isEmpty()) {
            sb.append("工具被过度调用可能陷入循环: ").append(overusedTools).append("。");
        }

        if (sb.length() > 0) {
            sb.append("建议检查Agent的工具使用策略。");
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    public record ToolCallAnalysis(
            Map<String, Long> toolCounts,
            List<String> unknownTools,
            List<String> overusedTools,
            boolean hasIssues,
            String guidance
    ) {
        public static ToolCallAnalysis noTools() {
            return new ToolCallAnalysis(Map.of(), List.of(), List.of(), false, null);
        }
    }
}