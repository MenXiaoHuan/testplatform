package com.example.platform.task.service;

import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import java.util.Map;
import org.slf4j.MDC;

final class TaskLogContext implements AutoCloseable {
    private final Map<String, String> previousContext;

    private TaskLogContext(Map<String, String> previousContext) {
        this.previousContext = previousContext;
    }

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

    static TaskLogContext open(TaskEntity task, SceneEntity scene) {
        TaskLogContext context = new TaskLogContext(MDC.getCopyOfContextMap());
        put("taskId", task == null ? null : task.getId());
        put("sceneId", scene == null ? null : scene.getId());
        put("repoId", task == null ? null : task.getRepoId());
        put("runnerName", task == null ? null : task.getRunnerName());
        return context;
    }

    void setStage(String stage) {
        put("stage", stage);
    }

    @Override
    public void close() {
        restore(previousContext);
    }

    private static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, String.valueOf(value));
    }

    private static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
