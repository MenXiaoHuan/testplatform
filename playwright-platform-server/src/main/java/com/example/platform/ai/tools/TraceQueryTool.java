package com.example.platform.ai.tools;

import com.example.platform.ai.AgentTraceLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Trace 查询工具 —— 让 LLM 自查 Agent 调用链路，便于回答「上次为什么这样」类问题。
 *
 * <p>三个 @Tool：
 * <ul>
 *   <li>{@link #queryTrace} —— 按 traceId 查询完整时间线，含阶段/级别/耗时摘要</li>
 *   <li>{@link #listRecentTraces} —— 列出最近 N 条 trace 摘要（默认 10，上限 50）</li>
 *   <li>{@link #getTraceStats} —— 返回存储统计</li>
 * </ul>
 */
@Component
public class TraceQueryTool {

    private static final Logger log = LoggerFactory.getLogger(TraceQueryTool.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentTraceLogService traceLogService;

    public TraceQueryTool(AgentTraceLogService traceLogService) {
        this.traceLogService = traceLogService;
    }

    @Tool(description = "Query the full execution trace chain by traceId. Returns all log entries for a specific AI agent request including stages, timing, and metadata.")
    public String queryTrace(
            @ToolParam(description = "The traceId (UUID) to query. Found in the AI response footer or API response.") String traceId) {
        log.info("AI tool: queryTrace, traceId={}", traceId);

        if (traceId == null || traceId.isBlank()) {
            return "traceId 不能为空。请提供一个有效的 traceId（UUID 格式）。";
        }

        List<AgentTraceLogService.TraceLogEntry> entries = traceLogService.queryByTraceId(traceId.trim());
        if (entries.isEmpty()) {
            return "未找到 traceId=" + traceId + " 的日志记录。该 traceId 可能已过期（保留90天）或输入有误。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【Trace 链路查询结果】traceId=").append(traceId).append("\n");
        sb.append("共 ").append(entries.size()).append(" 条日志记录\n\n");

        for (AgentTraceLogService.TraceLogEntry entry : entries) {
            String time = entry.timestamp().atZone(java.time.ZoneId.systemDefault())
                    .format(FORMATTER);
            String levelIcon = switch (entry.level()) {
                case "ERROR" -> "❌";
                case "WARN" -> "⚠️";
                default -> "✅";
            };

            sb.append("[").append(time).append("] ")
                    .append(levelIcon).append(" ")
                    .append("[").append(entry.stage()).append("] ")
                    .append(entry.message());

            if (entry.metadata() != null && !entry.metadata().isEmpty()) {
                sb.append(" | ");
                sb.append(entry.metadata().entrySet().stream()
                        .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                        .collect(Collectors.joining(", ")));
            }

            sb.append("\n");
        }

        sb.append("\n【链路摘要】\n");
        summarizeTrace(entries, sb);

        return sb.toString();
    }

    @Tool(description = "List recent agent call traces with summary information. Useful for finding traceIds of recent conversations.")
    public String listRecentTraces(
            @ToolParam(description = "Maximum number of recent traces to return, default 10, max 50") Integer limit) {
        int count = (limit != null) ? Math.min(limit, 50) : 10;
        log.info("AI tool: listRecentTraces, limit={}", count);

        List<AgentTraceLogService.TraceSummary> summaries = traceLogService.queryRecentTraces(count);
        if (summaries.isEmpty()) {
            return "暂无 Agent 调用记录。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【最近 Agent 调用记录】共 ").append(summaries.size()).append(" 条\n\n");

        for (int i = 0; i < summaries.size(); i++) {
            AgentTraceLogService.TraceSummary s = summaries.get(i);
            String time = s.lastUpdatedAt().atZone(java.time.ZoneId.systemDefault())
                    .format(FORMATTER);
            sb.append(i + 1).append(". ")
                    .append("traceId: ").append(s.traceId()).append("\n")
                    .append("   日志条数: ").append(s.entryCount()).append("\n")
                    .append("   最后更新: ").append(time).append("\n\n");
        }

        return sb.toString();
    }

    @Tool(description = "Get statistics about the agent trace log system including total stored traces.")
    public String getTraceStats() {
        long total = traceLogService.getTraceCount();
        return "【Agent Trace 存储统计】\n" +
                "- 存储的 trace 总数: " + total + "\n" +
                "- 保留策略: 90 天\n" +
                "- 单条 trace 最大日志数: 无上限\n" +
                "- 单条 trace 查询方式: 通过 queryTrace(traceId) 获取完整链路";
    }

    private void summarizeTrace(List<AgentTraceLogService.TraceLogEntry> entries, StringBuilder sb) {
        Map<String, Long> stageCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        AgentTraceLogService.TraceLogEntry::stage,
                        Collectors.counting()
                ));

        Map<String, Long> levelCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        AgentTraceLogService.TraceLogEntry::level,
                        Collectors.counting()
                ));

        sb.append("- 涉及阶段: ").append(String.join(", ", stageCounts.keySet())).append("\n");
        sb.append("- 日志级别分布: ");
        levelCounts.forEach((level, count) -> sb.append(level).append("=").append(count).append(" "));
        sb.append("\n");

        boolean hasError = entries.stream().anyMatch(e -> "ERROR".equals(e.level()));
        sb.append("- 状态: ").append(hasError ? "❌ 存在异常" : "✅ 正常").append("\n");

        Optional<AgentTraceLogService.TraceLogEntry> first = entries.stream().findFirst();
        Optional<AgentTraceLogService.TraceLogEntry> last = entries.stream().reduce((a, b) -> b);
        if (first.isPresent() && last.isPresent()) {
            long durationMs = last.get().timestamp().toEpochMilli() - first.get().timestamp().toEpochMilli();
            sb.append("- 耗时: ").append(durationMs).append("ms\n");
        }
    }
}