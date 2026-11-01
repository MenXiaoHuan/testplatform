package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** JSON Schema 校验评分器。依赖 json-schema-validator（可选依赖，失败降级返回 0）。 */
@Component
public class JsonSchemaEvaluator implements Evaluator {

    @Override public EvaluatorType type() { return EvaluatorType.JSON_SCHEMA; }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        String schemaJson = (String) config.get("schemaRef");
        if (schemaJson == null || schemaJson.isBlank()) {
            return new EvaluatorScore(type(), 0.0, "未配置 schema", Map.of());
        }
        if (agentOutput == null) {
            return new EvaluatorScore(type(), 0.0, "agentOutput 为空", Map.of());
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // 先尝试直接 JSON 解析输出
            Object outputNode;
            try {
                outputNode = mapper.readTree(agentOutput);
            } catch (Exception e) {
                return new EvaluatorScore(type(), 0.0, "agentOutput 不是合法 JSON: " + e.getMessage(), Map.of());
            }

            // 简单校验：schema 里的 required 字段必须存在
            com.fasterxml.jackson.databind.JsonNode schemaNode = mapper.readTree(schemaJson);
            var required = schemaNode.get("required");
            if (required != null && required.isArray()) {
                for (var field : required) {
                    String fieldName = field.asText();
                    if (outputNode instanceof com.fasterxml.jackson.databind.JsonNode outJson
                            && !outJson.has(fieldName)) {
                        return new EvaluatorScore(type(), 0.0,
                                "缺少 required 字段: " + fieldName, Map.of("missingField", fieldName));
                    }
                }
            }
            return new EvaluatorScore(type(), 1.0, "JSON 结构校验通过", Map.of());
        } catch (Exception e) {
            return new EvaluatorScore(type(), 0.0, "Schema 校验异常: " + e.getMessage(), Map.of());
        }
    }
}
