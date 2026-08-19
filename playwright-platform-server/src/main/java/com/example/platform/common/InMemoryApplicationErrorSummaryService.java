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

/**
 * 基于内存的应用错误摘要服务 —— 使用环形缓冲区保留最近的错误记录。
 *
 * <p>核心职责：
 * <ul>
 *   <li>以线程安全的 {@link ConcurrentLinkedDeque} 维护最多 {@value #MAX_ENTRIES} 条错误摘要</li>
 *   <li>记录时从 SLF4J {@link MDC} 中提取 requestId、traceId、taskId、sceneId、repoId、stage 等上下文</li>
 *   <li>按任务 ID（或场景/仓库 ID 兜底）查询最近的错误摘要</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link RequestCorrelationFilter} 注入的 MDC 上下文（requestId、traceId）</li>
 *   <li>任务执行过程中写入的 MDC 上下文（taskId、sceneId、repoId、stage）</li>
 * </ul>
 */
@Service
public class InMemoryApplicationErrorSummaryService implements ApplicationErrorSummaryService {

    /** 环形缓冲区最大容量。 */
    private static final int MAX_ENTRIES = 300;

    /** 错误摘要存储，新的条目插入队首，超出容量时从队尾淘汰。 */
    private final ConcurrentLinkedDeque<ApplicationErrorSummaryResponse> entries = new ConcurrentLinkedDeque<>();

    /**
     * 记录一次异常摘要，从 MDC 中提取链路上下文。
     */
    @Override
    public void recordError(String loggerName, String message, Throwable throwable) {
        ApplicationErrorSummaryResponse entry = new ApplicationErrorSummaryResponse(
                Instant.now(),
                loggerName,
                message,
                // 仅记录异常的简单类名，避免堆栈过长
                throwable == null ? null : throwable.getClass().getSimpleName(),
                MDC.get("requestId"),
                MDC.get("traceId"),
                parseLong(MDC.get("taskId")),
                parseLong(MDC.get("sceneId")),
                parseLong(MDC.get("repoId")),
                MDC.get("stage"));
        // 新条目插入队首，保证按时间倒序
        entries.addFirst(entry);
        // 超出容量时从队尾淘汰最旧的条目
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
    }

    /**
     * 查询指定任务最近的错误摘要。优先按 taskId 匹配，若条目无 taskId 则回退按 sceneId + repoId 匹配。
     */
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
        // 从队首（最新）开始遍历，命中后加入结果集，达到 limit 即停止
        for (ApplicationErrorSummaryResponse entry : entries) {
            if (matches(entry, task)) {
                matched.add(entry);
            }
            if (matched.size() >= normalizedLimit) {
                break;
            }
        }
        // 按发生时间倒序排序
        matched.sort(Comparator.comparing(ApplicationErrorSummaryResponse::occurredAt).reversed());
        return matched;
    }

    /**
     * 判断条目是否属于指定任务。优先精确匹配 taskId，无 taskId 时回退到 sceneId + repoId 匹配。
     */
    private boolean matches(ApplicationErrorSummaryResponse entry, TaskEntity task) {
        if (entry.taskId() != null && entry.taskId().equals(task.getId())) {
            return true;
        }
        // 条目存在 taskId 但不匹配，则直接排除
        if (entry.taskId() != null) {
            return false;
        }
        // 条目无 taskId 时，使用 sceneId 与 repoId 作为兜底匹配条件
        return equalsLong(entry.sceneId(), task.getSceneId()) && equalsLong(entry.repoId(), task.getRepoId());
    }

    /**
     * 判断两个 Long 值是否相等（两者均非 null 时才成立）。
     */
    private boolean equalsLong(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    /**
     * 尝试将字符串解析为 Long，解析失败返回 null。
     */
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
