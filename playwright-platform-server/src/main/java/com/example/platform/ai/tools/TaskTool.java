package com.example.platform.ai.tools;

import com.example.platform.task.service.TaskService;
import com.example.platform.task.model.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 测试任务工具 —— 让 LLM 列出任务或获取单个任务详情。
 *
 * <p>两个 @Tool：
 * <ul>
 *   <li>{@link #listTasks} —— 列出当前空间最多 20 个任务，可按 sceneId 过滤</li>
 *   <li>{@link #getTask} —— 按 ID 获取单个任务，含状态、结果、阶段日志预览</li>
 * </ul>
 */
@Component
public class TaskTool {

    private static final Logger log = LoggerFactory.getLogger(TaskTool.class);

    private final TaskService taskService;

    public TaskTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Tool(description = "List test tasks scoped to the current space, optionally filtered by scene ID")
    public String listTasks(
            @ToolParam(description = "Scene ID to filter by (optional)") Long sceneId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: listTasks, sceneId={}, spaceId={}", sceneId, spaceId);
        try {
            var tasks = sceneId != null
                    ? taskService.listByScene(spaceId, sceneId, 1, 20)
                    : taskService.list(spaceId, 1, 20);
            List<String> taskInfos = tasks.items().stream()
                    .map(t -> String.format("- [ID:%d] sceneId=%d, status=%s, trigger=%s, duration=%dms, passed=%d, failed=%d, queued=%s",
                            t.id(), t.sceneId(), t.status(), t.triggerType(),
                            t.durationMs() != null ? t.durationMs() : 0,
                            t.passedCount(), t.failedCount(), t.queuedAt()))
                    .collect(Collectors.toList());
            if (taskInfos.isEmpty()) {
                return "No tasks found" + (sceneId != null ? " for scene " + sceneId : "") + " in space " + spaceId;
            }
            return "Found " + taskInfos.size() + " tasks in space " + spaceId + ":\n" + String.join("\n", taskInfos);
        } catch (Exception e) {
            log.error("listTasks failed", e);
            return "Error listing tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Get detailed information about a specific task by ID including status, results, and stage logs")
    public String getTask(
            @ToolParam(description = "Task ID") Long taskId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: getTask, id={}, spaceId={}", taskId, spaceId);
        try {
            TaskEntity task = taskService.get(spaceId, taskId);
            if (task == null) {
                return "Task not found: " + taskId + " in space " + spaceId;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Task [ID:%d]: status=%s, sceneId=%d, repoId=%d, trigger=%s/%s, " +
                            "branch=%s, duration=%dms, passed=%d, failed=%d, skipped=%d%n",
                    task.getId(), task.getStatus(), task.getSceneId(), task.getRepoId(),
                    task.getTriggerType(), task.getTriggerReason(), task.getBranch(),
                    task.getDurationMs() != null ? task.getDurationMs() : 0,
                    task.getPassedCount(), task.getFailedCount(), task.getSkippedCount()));

            if (task.getResultMessage() != null && !task.getResultMessage().isBlank()) {
                sb.append("Result: ").append(task.getResultMessage()).append("\n");
            }

            var stageLogs = taskService.listStageLogs(spaceId, taskId);
            if (!stageLogs.isEmpty()) {
                sb.append("\nStage logs:\n");
                stageLogs.forEach(sl -> sb.append(String.format("  - [%s] stream=%s, lines=%d, preview=%s%n",
                        sl.stage(), sl.streamType(), sl.lineCount(),
                        sl.previewText() != null && !sl.previewText().isBlank()
                                ? sl.previewText().substring(0, Math.min(sl.previewText().length(), 200))
                                : "(no preview)")));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("getTask failed", e);
            return "Error getting task: " + e.getMessage();
        }
    }
}
