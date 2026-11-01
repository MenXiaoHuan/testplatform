package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import com.example.platform.evaluation.langfuse.ScoreWriteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LLM-as-Judge 评分器 —— 通过 Spring AI ChatModel 执行 Judge Prompt 得到评分。
 * 要求 Judge 返回 JSON（也兼容旧的 "Score: 80/100" 格式）。
 *
 * <p>C18 三层预算墙：JudgeBudgetController 先过，预算耗尽或 LLM 调用失败自动降级为 RuleMatchEvaluator。
 */
@Component
public class LlmAsJudgeEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmAsJudgeEvaluator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_AGENT_OUTPUT_CHARS = 4000;
    private static final int MAX_GOLDEN_CHARS = 2000;

    private final JudgeBudgetController budget;
    private final LangfuseClient langfuse;
    private final RuleMatchEvaluator fallbackEvaluator;  // 预算耗尽 / LLM 失败自动降级
    private final ChatModel chatModel;

    public LlmAsJudgeEvaluator(JudgeBudgetController budget, LangfuseClient langfuse,
                               RuleMatchEvaluator fallbackEvaluator, ChatModel chatModel) {
        this.budget = budget;
        this.langfuse = langfuse;
        this.fallbackEvaluator = fallbackEvaluator;
        this.chatModel = chatModel;
    }

    @Override public EvaluatorType type() { return EvaluatorType.LLM_AS_JUDGE; }

    @Override
    public EvaluatorScore score(String agentOutput, String goldenAnswer,
                                Map<String, Object> context, Map<String, Object> config) {
        // C18 三层预算墙检查
        if (!budget.tryConsume("llm_as_judge", "per-policy")) {
            log.warn("Judge budget exhausted, auto-downgrade to RuleMatch");
            return fallbackEvaluator.score(agentOutput, goldenAnswer, context, config);
        }

        try {
            String judgeOutput = callJudge(agentOutput, goldenAnswer, context, config);
            EvaluatorScore parsed = parseJudgeOutput(judgeOutput);

            // 回写 Langfuse Score
            String traceId = (String) context.get("langfuseTraceId");
            if (traceId != null && parsed.score() != null) {
                langfuse.writeScore(ScoreWriteRequest.builder()
                        .traceId(traceId).name("llm_as_judge")
                        .value(parsed.score()).comment(parsed.reason()).build());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("LlmAsJudge failed, downgrade: {}", e.getMessage());
            return fallbackEvaluator.score(agentOutput, goldenAnswer, context, config);
        }
    }

    /** 调用真实 ChatModel 做 Judge，要求模型返回 JSON。 */
    private String callJudge(String agentOutput, String goldenAnswer,
                             Map<String, Object> context, Map<String, Object> config) {
        String output = truncate(agentOutput, MAX_AGENT_OUTPUT_CHARS);
        String golden = truncate(goldenAnswer, MAX_GOLDEN_CHARS);
        String originalInput = context != null && context.get("originalInput") != null
                ? truncate(String.valueOf(context.get("originalInput")), MAX_GOLDEN_CHARS) : null;
        String referenceContext = context != null && context.get("referenceContext") != null
                ? truncate(String.valueOf(context.get("referenceContext")), MAX_GOLDEN_CHARS) : null;

        String requirement = config != null && config.get("requirement") != null
                ? String.valueOf(config.get("requirement"))
                : "请从准确性、完整性和相关性三个维度评价 Agent 输出。";

        String systemPrompt = """
                你是一名严格、客观的大模型评测裁判。
                评分时必须结合「用户原始输入」，判断 Agent 输出是否真正回应了用户的问题，而不是仅与参考答案相似。
                只输出一个 JSON 对象，不要输出 Markdown 代码块或额外解释：
                {"score": <0-100 的数字>, "reason": "<简洁的中文评分理由>"}
                """;
        StringBuilder user = new StringBuilder();
        user.append("评分要求：").append(requirement).append("\n\n");
        user.append("用户原始输入：\n").append(originalInput != null && !originalInput.isBlank() ? originalInput : "（未提供）").append("\n\n");
        user.append("参考上下文：\n").append(referenceContext != null && !referenceContext.isBlank() ? referenceContext : "（未提供）").append("\n\n");
        user.append("Agent 输出：\n").append(output).append("\n\n");
        user.append("参考答案：\n").append(golden != null && !golden.isBlank() ? golden : "（无参考答案，请按输出质量评分）");

        String raw = chatModel.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(user.toString()))))
                .getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Judge 返回为空");
        }
        return raw.trim();
    }

    private EvaluatorScore parseJudgeOutput(String judgeOutput) {
        String cleaned = stripCodeFence(judgeOutput);
        try {
            JsonNode node = JSON.readTree(cleaned);
            double score = node.path("score").asDouble(Double.NaN);
            if (Double.isNaN(score)) throw new IllegalStateException("缺少 score 字段");
            String reason = node.path("reason").asText("");
            return new EvaluatorScore(EvaluatorType.LLM_AS_JUDGE,
                    clamp(score), reason, Map.of("raw", truncate(judgeOutput, 1000)));
        } catch (Exception ignored) {
            // 兼容旧格式："Score: 80/100\nReason: xxx"
        }
        try {
            int colon = judgeOutput.indexOf("Score:");
            int slash = judgeOutput.indexOf("/", colon);
            if (colon < 0 || slash < 0) throw new IllegalStateException("Judge 输出格式异常");
            double score = Double.parseDouble(judgeOutput.substring(colon + 6, slash).trim());
            String reason = judgeOutput.contains("Reason:")
                    ? judgeOutput.substring(judgeOutput.indexOf("Reason:") + 8).trim() : "";
            return new EvaluatorScore(EvaluatorType.LLM_AS_JUDGE,
                    clamp(score), reason, Map.of("raw", truncate(judgeOutput, 1000)));
        } catch (Exception e) {
            return new EvaluatorScore(EvaluatorType.LLM_AS_JUDGE, 0.0,
                    "Judge 输出解析失败: " + e.getMessage(), Map.of("raw", truncate(judgeOutput, 1000)));
        }
    }

    private static String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private static double clamp(double score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
