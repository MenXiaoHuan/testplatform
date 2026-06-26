package com.example.platform.common;

import com.example.platform.task.dto.ApplicationErrorSummaryResponse;
import com.example.platform.task.model.TaskEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class InMemoryApplicationErrorSummaryService implements ApplicationErrorSummaryService {
    private static final int MAX_ENTRIES = 300;

    private final ConcurrentLinkedDeque<ApplicationErrorSummaryResponse> entries = new ConcurrentLinkedDeque<>();

    @Override
    public void recordError(String loggerName, String message, Throwable throwable) {
        ApplicationErrorSummaryResponse entry = new ApplicationErrorSummaryResponse(
                Instant.now(),
                loggerName,
                message,
                throwable == null ? null : throwable.getClass().getSimpleName(),
                MDC.get("requestId"),
                MDC.get("traceId"),
                parseLong(MDC.get("taskId")),
                parseLong(MDC.get("sceneId")),
                parseLong(MDC.get("repoId")),
                MDC.get("stage"));
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
    }

    @Override
    public List<ApplicationErrorSummaryResponse> listRecentForTask(TaskEntity task, int limit) {
        if (task == null || task.getId() == null) {
            return List.of();
        }
        int normalizedLimit = Math.max(0, limit);
        if (normalizedLimit == 0) {
            return List.of();
        }
        List<ApplicationErrorSummaryResponse> matched = new ArrayList<>();
        for (ApplicationErrorSummaryResponse entry : entries) {
            if (matches(entry, task)) {
                matched.add(entry);
            }
            if (matched.size() >= normalizedLimit) {
                break;
            }
        }
        matched.sort(Comparator.comparing(ApplicationErrorSummaryResponse::occurredAt).reversed());
        return matched;
    }

    private boolean matches(ApplicationErrorSummaryResponse entry, TaskEntity task) {
        if (entry.taskId() != null && entry.taskId().equals(task.getId())) {
            return true;
        }
        if (entry.taskId() != null) {
            return false;
        }
        return equalsLong(entry.sceneId(), task.getSceneId()) && equalsLong(entry.repoId(), task.getRepoId());
    }

    private boolean equalsLong(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
