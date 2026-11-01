package com.example.platform.evaluation.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ExactMatchEvaluator 单测 —— trim + ignoreCase 开关。 */
class ExactMatchEvaluatorTest {

    private ExactMatchEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ExactMatchEvaluator();
    }

    @Test
    void shouldMatchExactly() {
        var result = evaluator.score("hello", "hello", null, Map.of());
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldNotMatchDifferentStrings() {
        var result = evaluator.score("hello", "world", null, Map.of());
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldTrimByDefault() {
        var result = evaluator.score("  hello  ", "hello", null, Map.of());
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldNotTrimWhenTrimDisabled() {
        var result = evaluator.score("  hello  ", "hello", null, Map.of("trim", false));
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldIgnoreCaseWhenConfigured() {
        var result = evaluator.score("HELLO", "hello", null, Map.of("ignoreCase", true));
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnZeroOnNullGolden() {
        var result = evaluator.score("hello", null, null, Map.of());
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroOnNullOutput() {
        var result = evaluator.score(null, "hello", null, Map.of());
        assertThat(result.score()).isEqualTo(0.0);
    }
}
