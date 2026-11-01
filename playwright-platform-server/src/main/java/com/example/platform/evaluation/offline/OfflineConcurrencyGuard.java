package com.example.platform.evaluation.offline;

import com.example.platform.evaluation.config.EvaluationProperties;
import com.example.platform.evaluation.exception.EvalRunConcurrencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * C15 per-space + C16 per-Target 双层并发护栏。
 * 在 EvalRun 启动前检查：同 space 不能超过 maxRunningPerSpace 条 RUNNING/PENDING；
 * 同 Target 不能超过 maxTargetConcurrentRuns 条。
 */
@Service
public class OfflineConcurrencyGuard {

    private static final Logger log = LoggerFactory.getLogger(OfflineConcurrencyGuard.class);

    private final EvalRunMapper runMapper;
    private final EvaluationProperties props;

    public OfflineConcurrencyGuard(EvalRunMapper runMapper, EvaluationProperties props) {
        this.runMapper = runMapper;
        this.props = props;
    }

    /** 护栏入口 —— 失败抛 EvalRunConcurrencyException。 */
    public void checkAndAcquire(Long spaceId, Long targetId) {
        // C15
        long spaceRunning = runMapper.countRunningBySpace(spaceId);
        if (spaceRunning >= props.getOffline().getMaxRunningPerSpace()) {
            log.warn("C15 guard hit: spaceId={}, running={}, max={}",
                    spaceId, spaceRunning, props.getOffline().getMaxRunningPerSpace());
            throw new EvalRunConcurrencyException("SPACE_GUARDIAN_HIT",
                    "当前 space 已有 " + props.getOffline().getMaxRunningPerSpace() +
                            " 条评测在运行，请等待后重试");
        }
        // C16
        long targetRunning = runMapper.countRunningByTarget(targetId);
        if (targetRunning >= props.getOffline().getMaxTargetConcurrentRuns()) {
            log.warn("C16 guard hit: targetId={}, running={}, max={}",
                    targetId, targetRunning, props.getOffline().getMaxTargetConcurrentRuns());
            throw new EvalRunConcurrencyException("TARGET_GUARDIAN_HIT",
                    "该 Target 被 " + props.getOffline().getMaxTargetConcurrentRuns() +
                            " 条评测占用，请稍后重试");
        }
    }
}
