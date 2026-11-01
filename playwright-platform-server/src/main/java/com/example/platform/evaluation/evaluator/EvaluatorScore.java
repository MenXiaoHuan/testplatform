package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;

import java.util.Map;

/** Evaluator 输出 DTO。 */
public record EvaluatorScore(
        EvaluatorType evaluatorType,
        Double score,
        String reason,
        Map<String, Object> metadata) {}
