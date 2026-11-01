package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.common.EvalRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * C20 Run 状态机乐观锁 —— 所有状态转移必须带 version 条件。
 * version 冲突时调用方重试最多 3 次。
 */
@Service
public class OfflineRunStatusMutator {

    private static final Logger log = LoggerFactory.getLogger(OfflineRunStatusMutator.class);
    private static final int MAX_RETRY = 3;

    private final EvalRunMapper mapper;

    public OfflineRunStatusMutator(EvalRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 条件转移：必须 fromStatus + version 匹配；冲突返回 false。 */
    public boolean transition(Long runId, String fromStatus, String toStatus) {
        // 1. 先查当前 version
        EvalRunEntity current = mapper.findById(runId).orElse(null);
        if (current == null) return false;
        if (!fromStatus.equals(current.getStatus())) return false;
        long expectedVersion = current.getVersion();

        // 2. 条件 UPDATE
        // startedAt 只在 RUNNING 第一次设
        String startedClause = EvalRunStatus.RUNNING.name().equals(toStatus)
                ? "started_at = current_timestamp," : "";
        int updated = mapper.updateStatusAndVersion(runId, fromStatus, toStatus, expectedVersion, startedClause);
        if (updated == 0) {
            log.debug("version conflict: runId={}, from={}, to={}, expectedVersion={}", runId, fromStatus, toStatus, expectedVersion);
        }
        return updated > 0;
    }

    /** 重试 3 次的状态转移。 */
    public boolean transitionWithRetry(Long runId, String fromStatus, String toStatus) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            if (transition(runId, fromStatus, toStatus)) return true;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    /** 任何状态 → CANCELLED（不走乐观锁，直接 force）。 */
    public void cancel(Long runId) {
        mapper.forceStatusUpdate(runId, EvalRunStatus.CANCELLED.name(), 0, 0, null);
    }
}
