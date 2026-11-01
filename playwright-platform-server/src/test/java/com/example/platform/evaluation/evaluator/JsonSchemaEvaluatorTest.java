package com.example.platform.evaluation.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** JsonSchemaEvaluator 单测 —— required 字段检查 + JSON 合法性。 */
class JsonSchemaEvaluatorTest {

    private JsonSchemaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JsonSchemaEvaluator();
    }

    @Test
    void shouldPassWhenAllRequiredFieldsPresent() {
        String schema = "{\"required\": [\"name\", \"age\"]}";
        String output = "{\"name\": \"Alice\", \"age\": 30}";
        var result = evaluator.score(output, null, null, Map.of("schemaRef", schema));
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.reason()).contains("通过");
    }

    @Test
    void shouldFailWhenMissingRequiredField() {
        String schema = "{\"required\": [\"name\", \"age\"]}";
        String output = "{\"name\": \"Alice\"}";
        var result = evaluator.score(output, null, null, Map.of("schemaRef", schema));
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("缺少 required 字段: age");
        assertThat(result.metadata()).containsEntry("missingField", "age");
    }

    @Test
    void shouldFailOnInvalidJson() {
        String schema = "{\"required\": [\"name\"]}";
        var result = evaluator.score("not-json-at-all", null, null, Map.of("schemaRef", schema));
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("不是合法 JSON");
    }

    @Test
    void shouldReturnZeroWhenSchemaMissing() {
        var result = evaluator.score("{\"a\":1}", null, null, Map.of());
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("未配置 schema");
    }

    @Test
    void shouldReturnZeroOnNullOutput() {
        var result = evaluator.score(null, null, null,
                Map.of("schemaRef", "{\"required\":[\"x\"]}"));
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldPassWhenNoRequiredArray() {
        // schema 没有 required 字段时，只要是合法 JSON 就过
        String schema = "{\"type\": \"object\"}";
        var result = evaluator.score("{\"anything\": 123}", null, null, Map.of("schemaRef", schema));
        assertThat(result.score()).isEqualTo(1.0);
    }
}
