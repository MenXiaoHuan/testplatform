package com.example.platform.ai.tools;

import com.example.platform.task.service.TaskService;
import com.example.platform.task.dto.TaskStageLogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LogPreprocessingTool {

    private static final Logger log = LoggerFactory.getLogger(LogPreprocessingTool.class);

    private final TaskService taskService;

    public LogPreprocessingTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Tool(description = "Preprocess and analyze task execution logs within the current space, extract error summaries and key information")
    public String analyzeLogs(
            @ToolParam(description = "Task ID whose logs should be analyzed") Long taskId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: analyzeLogs, taskId={}, spaceId={}", taskId, spaceId);
        try {
            var task = taskService.get(spaceId, taskId);
            if (task == null) {
                return "Task not found: " + taskId + " in space " + spaceId;
            }

            List<TaskStageLogResponse> stageLogs = taskService.listStageLogs(spaceId, taskId);
            if (stageLogs.isEmpty()) {
                return "No stage logs found for task " + taskId + " in space " + spaceId + ". Task status: " + task.getStatus();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Log analysis for Task [ID:").append(taskId).append("] in space ").append(spaceId).append(":\n");
            sb.append("Status: ").append(task.getStatus()).append("\n");
            sb.append("Result: ").append(task.getResultMessage() != null ? task.getResultMessage() : "N/A").append("\n\n");

            for (TaskStageLogResponse stageLog : stageLogs) {
                sb.append(String.format("=== Stage: %s (stream: %s, lines: %d) ===%n",
                        stageLog.stage(), stageLog.streamType(), stageLog.lineCount()));

                String preview = stageLog.previewText();
                if (preview != null && !preview.isBlank()) {
                    sb.append(extractErrorSummary(preview)).append("\n");
                }
            }

            if (task.getFailedCount() > 0) {
                sb.append("\n=== Case Results Summary ===\n");
                var caseResults = taskService.listCaseResultResponses(spaceId, taskId);
                long failed = caseResults.stream().filter(c -> "FAILED".equalsIgnoreCase(c.status())).count();
                long passed = caseResults.stream().filter(c -> "PASSED".equalsIgnoreCase(c.status())).count();
                sb.append(String.format("Passed: %d, Failed: %d, Total: %d%n",
                        passed, failed, caseResults.size()));

                caseResults.stream()
                        .filter(c -> "FAILED".equalsIgnoreCase(c.status()))
                        .limit(5)
                        .forEach(c -> sb.append(String.format("  FAILED: %s - %s%n",
                                c.fullName() != null ? c.fullName() : c.id(),
                                c.errorMessage() != null ? c.errorMessage() : "no error details")));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("analyzeLogs failed", e);
            return "Error analyzing logs: " + e.getMessage();
        }
    }

    private String extractErrorSummary(String text) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder errorLines = new StringBuilder();
        int errorCount = 0;
        int limit = 10;

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("error") || lower.contains("fail") || lower.contains("exception")
                    || lower.contains("assert") || lower.contains("timeout") || lower.contains("failed")) {
                if (errorCount < limit) {
                    errorLines.append("  | ").append(line.trim()).append("\n");
                    errorCount++;
                }
            }
        }

        if (errorCount == 0) {
            String firstLine = lines.length > 0 ? lines[0].trim() : "(empty)";
            return "  Preview: " + (firstLine.length() > 200 ? firstLine.substring(0, 200) + "..." : firstLine);
        }

        return "  Errors found (" + errorCount + "):\n" + errorLines;
    }
}
