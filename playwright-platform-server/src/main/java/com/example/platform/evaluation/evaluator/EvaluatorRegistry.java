package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Evaluator 注册表 —— 按 EvaluatorType 路由到具体评分器实现。 */
@Component
public class EvaluatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(EvaluatorRegistry.class);

    private final Map<EvaluatorType, Evaluator> evaluators = new EnumMap<>(EvaluatorType.class);

    public EvaluatorRegistry(List<Evaluator> all) {
        for (Evaluator evaluator : all) {
            evaluators.put(evaluator.type(), evaluator);
        }
        log.info("EvaluatorRegistry initialized with {} evaluators: {}", evaluators.size(), evaluators.keySet());
    }

    public Optional<Evaluator> find(EvaluatorType type) {
        return Optional.ofNullable(evaluators.get(type));
    }
}
