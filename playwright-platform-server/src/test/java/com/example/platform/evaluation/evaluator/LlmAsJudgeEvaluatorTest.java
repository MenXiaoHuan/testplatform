package com.example.platform.evaluation.evaluator;

import com.example.platform.evaluation.common.EvaluatorType;
import com.example.platform.evaluation.langfuse.LangfuseClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LlmAsJudgeEvaluator 单测 —— 真实 ChatModel 调用、JSON 解析与预算降级。 */
class LlmAsJudgeEvaluatorTest {

    @Test
    void shouldCallChatModelAndParseJsonScore() {
        JudgeBudgetController budget = mock(JudgeBudgetController.class);
        when(budget.tryConsume(any(), any())).thenReturn(true);

        ChatModel chatModel = mock(ChatModel.class);
        AssistantMessage message = new AssistantMessage(
                "```json\n{\"score\": 88, \"reason\": \"答案准确且完整\"}\n```");
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(message))));

        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(
                budget, mock(LangfuseClient.class), new RuleMatchEvaluator(), chatModel);

        EvaluatorScore score = evaluator.score("42", "42", Map.of("langfuseTraceId", "t1"), Map.of());

        assertEquals(88.0, score.score());
        assertEquals("答案准确且完整", score.reason());
        assertNotNull(score.metadata().get("raw"));
    }

    @Test
    void shouldFallbackToRuleMatchWhenBudgetExhausted() {
        JudgeBudgetController budget = mock(JudgeBudgetController.class);
        when(budget.tryConsume(any(), any())).thenReturn(false);

        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(
                budget, mock(LangfuseClient.class), new RuleMatchEvaluator(), mock(ChatModel.class));

        EvaluatorScore score = evaluator.score("成功完成", "成功",
                Map.of(), Map.of("keywords", List.of("成功")));

        assertEquals(100.0, score.score());
        assertEquals(EvaluatorType.RULE_MATCH, score.evaluatorType());
    }
}
