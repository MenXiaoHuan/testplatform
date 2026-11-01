package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;

import java.util.Map;

/** 评分器核心接口 —— 7 种实现全部实现此接口。 */
public interface Evaluator {

    EvaluatorType type();

    /**
     * 执行评分。
     *
     * @param agentOutput 被评 Target 的输出
     * @param goldenAnswer Golden Answer（可选）
     * @param context     额外上下文（reference_context / metadata）
     * @param config      评分器配置（每种类型不同 JSON 解析后 Map）
     */
    EvaluatorScore score(String agentOutput, String goldenAnswer,
                         Map<String, Object> context, Map<String, Object> config);
}
