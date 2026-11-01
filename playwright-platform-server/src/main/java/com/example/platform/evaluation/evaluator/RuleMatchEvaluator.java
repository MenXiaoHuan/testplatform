package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 规则匹配评分器 —— keywords OR/AND + 正则匹配。 */
@Component
public class RuleMatchEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleMatchEvaluator.class);

    @Override public EvaluatorType type() { return EvaluatorType.RULE_MATCH; }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        if (agentOutput == null || agentOutput.isBlank()) {
            return new EvaluatorScore(type(), 0.0, "output 为空", Map.of());
        }

        // 正则模式
        String patternStr = (String) config.get("pattern");
        if (patternStr != null && !patternStr.isBlank()) {
            try {
                Pattern p = Pattern.compile(patternStr);
                boolean match = p.matcher(agentOutput).find();
                return new EvaluatorScore(type(), match ? 100.0 : 0.0,
                        match ? "正则匹配成功" : "正则匹配失败", Map.of("pattern", patternStr));
            } catch (Exception e) {
                return new EvaluatorScore(type(), 0.0, "正则编译失败: " + e.getMessage(), Map.of());
            }
        }

        // keywords OR/AND：样本自带关键词优先，样本未配置时回退到评估器配置
        List<String> sampleKeywords = keywordList(context == null ? null : context.get("sampleKeywords"));
        List<String> configuredKeywords = keywordList(config.get("keywords"));
        List<String> keywords = sampleKeywords.isEmpty() ? configuredKeywords : sampleKeywords;
        boolean fromSample = !sampleKeywords.isEmpty();
        boolean matchAll = Boolean.TRUE.equals(config.get("matchAll"));
        double scoreOnMatch = toDouble(config.get("scoreOnMatch"), 100.0);

        if (keywords.isEmpty()) {
            return new EvaluatorScore(type(), 0.0, "样本与评估器均未配置 keywords", Map.of());
        }
        int matched = 0;
        for (String kw : keywords) {
            if (agentOutput.toLowerCase().contains(kw.toLowerCase())) matched++;
        }

        double score;
        String reason;
        if (matchAll) {
            boolean fullMatch = matched == keywords.size();
            score = fullMatch ? scoreOnMatch : 0;
            reason = fullMatch ? "全部关键词匹配" : "仅匹配 " + matched + "/" + keywords.size();
        } else {
            score = scoreOnMatch * matched / keywords.size();
            reason = "匹配 " + matched + "/" + keywords.size();
        }
        return new EvaluatorScore(type(), score, reason,
                Map.of("matched", matched, "total", keywords.size(),
                        "keywordSource", fromSample ? "sample" : "config"));
    }

    /** 统一把 List / 逗号分隔字符串解析成关键词列表。 */
    private static List<String> keywordList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return java.util.Arrays.stream(text.split("[,，、\\s]+"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    private static double toDouble(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }
}
