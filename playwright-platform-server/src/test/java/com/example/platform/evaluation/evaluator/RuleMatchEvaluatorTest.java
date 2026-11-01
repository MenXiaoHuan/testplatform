package com.example.platform.evaluation.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RuleMatchEvaluator 单测 —— keywords OR/AND + 正则。 */
class RuleMatchEvaluatorTest {

    private RuleMatchEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RuleMatchEvaluator();
    }

    // ========== OR（默认）: 命中部分关键词，按比例得分 ==========

    @Test
    void shouldScorePartialMatchInOrMode() {
        var result = evaluator.score(
                "这是一个成功的登录流程，已完成验证", null, null,
                Map.of("keywords", List.of("成功", "登录", "完成", "失败")));
        // 4 个关键词命中 3 个 → 3/4 * 100 = 75
        assertThat(result.score()).isEqualTo(75.0);
        assertThat(result.reason()).contains("匹配 3/4");
    }

    @Test
    void shouldReturnZeroWhenNoKeywordMatched() {
        var result = evaluator.score(
                "完全不相关的输出", null, null,
                Map.of("keywords", List.of("成功", "失败")));
        assertThat(result.score()).isEqualTo(0.0);
    }

    // ========== AND（matchAll=true）: 全部命中才 100 ==========

    @Test
    void shouldRequireAllKeywordsInAndMode() {
        var result = evaluator.score(
                "成功完成登录流程", null, null,
                Map.of("keywords", List.of("成功", "完成", "登录"), "matchAll", true));
        assertThat(result.score()).isEqualTo(100.0);
    }

    @Test
    void shouldReturnZeroWhenMissingKeywordInAndMode() {
        var result = evaluator.score(
                "成功登录但任务中断", null, null,
                Map.of("keywords", List.of("成功", "完成", "登录"), "matchAll", true));
        // 输出里没有"完成"（"任务中断" 不包含子串 "完成"）
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("仅匹配 2/3");
    }

    // ========== 自定义 scoreOnMatch ==========

    @Test
    void shouldUseCustomScoreOnMatch() {
        var result = evaluator.score(
                "成功成功成功", null, null,
                Map.of("keywords", List.of("成功", "失败"), "scoreOnMatch", 50.0));
        // 命中 1/2，分数 = 50 * 1/2 = 25
        assertThat(result.score()).isEqualTo(25.0);
    }

    // ========== 正则模式 ==========

    @Test
    void shouldMatchRegex() {
        var result = evaluator.score(
                "订单号：ORD-12345 已确认", null, null,
                Map.of("pattern", "ORD-\\d+"));
        assertThat(result.score()).isEqualTo(100.0);
        assertThat(result.reason()).contains("正则匹配成功");
    }

    @Test
    void shouldFailRegexWhenNoMatch() {
        var result = evaluator.score(
                "没有订单号", null, null,
                Map.of("pattern", "ORD-\\d+"));
        assertThat(result.score()).isEqualTo(0.0);
    }

    // ========== 边界 ==========

    @Test
    void shouldReturnZeroWhenOutputBlank() {
        var result = evaluator.score("   ", null, null,
                Map.of("keywords", List.of("a")));
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("为空");
    }

    @Test
    void shouldReturnZeroWhenNoKeywordsNorPattern() {
        var result = evaluator.score("anything", null, null, Map.of());
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("未配置");
    }
}
