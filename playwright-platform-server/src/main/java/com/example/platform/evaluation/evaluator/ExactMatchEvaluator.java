package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 精确匹配评分器；支持从 Agent 输出中按正则抽取答案后再比较。 */
@Component
public class ExactMatchEvaluator implements Evaluator {

    @Override public EvaluatorType type() { return EvaluatorType.EXACT_MATCH; }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        if (goldenAnswer == null) {
            return new EvaluatorScore(type(), 0.0, "goldenAnswer 为空", Map.of());
        }
        if (agentOutput == null) {
            return new EvaluatorScore(type(), 0.0, "agentOutput 为空", Map.of());
        }
        boolean trim = !Boolean.FALSE.equals(config.get("trim"));
        boolean ignoreCase = Boolean.TRUE.equals(config.get("ignoreCase"));

        String a;
        String b = goldenAnswer;
        try {
            // 输出抽取：例如从「答案是 B，因为…」提取 B，从多步算式提取最后一个数值
            Object outputPattern = config.get("extractPattern");
            if (outputPattern instanceof String p && !p.isBlank()) {
                a = extract(agentOutput, p, Boolean.TRUE.equals(config.get("extractLast")));
                if (a == null) {
                    return new EvaluatorScore(type(), 0.0,
                            "输出未匹配答案抽取规则", Map.of("pattern", p));
                }
            } else {
                a = agentOutput;
            }

            // 参考答案抽取：例如 gsm8k 从 "#### 72" 中提取 72；未命中时保留原文
            Object goldenPattern = config.get("goldenExtractPattern");
            if (goldenPattern instanceof String gp && !gp.isBlank()) {
                String extractedGolden = extract(goldenAnswer, gp, false);
                if (extractedGolden != null) b = extractedGolden;
            }
        } catch (Exception e) {
            return new EvaluatorScore(type(), 0.0, "答案抽取正则无效: " + e.getMessage(), Map.of());
        }

        if (trim) { a = a.trim(); b = b.trim(); }
        if (ignoreCase) { a = a.toLowerCase(); b = b.toLowerCase(); }

        double score = a.equals(b) ? 1.0 : 0.0;
        return new EvaluatorScore(type(), score,
                score == 1.0 ? "精确匹配" : "输出与答案不同",
                Map.of("actual", a, "expected", b));
    }

    /** 按正则抽取；last=true 时取最后一个匹配，否则取第一个；有分组时取第 1 组。返回 null 表示未命中。 */
    private static String extract(String text, String pattern, boolean last) {
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        String result = null;
        while (matcher.find()) {
            result = matcher.groupCount() >= 1 && matcher.group(1) != null
                    ? matcher.group(1) : matcher.group();
            if (!last) break;
        }
        return result;
    }
}
