package com.example.platform.task.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 任务日志上下文 —— 基于 SLF4J MDC（Mapped Diagnostic Context）的线程级日志上下文管理。
 * 用于在任务执行期间将任务 ID、场景 ID、仓库 ID 等信息注入日志，便于日志追踪和过滤。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #wrap(Runnable)} —— 包装 Runnable，在新线程中传递 MDC 上下文</li>
 *   <li>{@link #open(TaskEntity, SceneEntity)} —— 打开日志上下文，注入任务和场景信息</li>
 *   <li>{@link #setStage(String)} —— 设置当前执行阶段标记</li>
 *   <li>{@link #close()} —— 恢复之前的 MDC 上下文</li>
 * </ul>
 */
final class TaskLogContext implements AutoCloseable {
    private final Map<String, String> previousContext;

    private TaskLogContext(Map<String, String> previousContext) {
        this.previousContext = previousContext;
    }

    /**
     * 包装 Runnable，将当前线程的 MDC 上下文传递到新线程中执行。
     */
    static Runnable wrap(Runnable delegate) {
        Map<String, String> capturedContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            restore(capturedContext);
            try {
                delegate.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 打开日志上下文，注入任务 ID、场景 ID、仓库 ID 等信息到 MDC。
     */
    static TaskLogContext open(TaskEntity task, SceneEntity scene) {
        TaskLogContext context = new TaskLogContext(MDC.getCopyOfContextMap());
        put("taskId", task == null ? null : task.getId());
        put("sceneId", scene == null ? null : scene.getId());
        put("repoId", task == null ? null : task.getRepoId());
        put("runnerName", task == null ? null : task.getRunnerName());
        return context;
    }

    /**
     * 设置当前执行阶段标记到 MDC。
     */
    void setStage(String stage) {
        put("stage", stage);
    }

    /**
     * 关闭上下文，恢复之前的 MDC 状态。
     */
    @Override
    public void close() {
        restore(previousContext);
    }

    /**
     * 将键值对设置到 MDC，值为 null 时移除该键。
     */
    private static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, String.valueOf(value));
    }

    /**
     * 恢复 MDC 上下文，空上下文时清除所有 MDC 值。
     */
    private static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
