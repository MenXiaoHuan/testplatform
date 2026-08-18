package com.example.platform.ai.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 上下文压缩服务 —— 当会话 token 数或消息数超过阈值时，对历史消息做摘要压缩。
 *
 * <p>触发条件（满足任一即触发）：
 * <ul>
 *   <li>预估 token 数 > {@code maxTokens * compressionThreshold}（默认 0.8）</li>
 *   <li>消息数 > {@code maxMessages}（默认 50）</li>
 * </ul>
 *
 * <p>压缩策略：
 * <ul>
 *   <li>{@link #applySmartCompression} —— 智能压缩：保留最近 N 条原文 + 对更早消息做 LLM 摘要</li>
 *   <li>{@link #applyAggressiveCompression} —— 激进压缩：当超过 maxTokens 时触发，只保留最近 N 条 user/assistant</li>
 *   <li>{@link #truncateMessagesToFit} —— 兜底：从最近往前保留到 80% maxTokens</li>
 * </ul>
 *
 * <p>LLM 摘要失败/超时（30s）/未启用时，回退到规则摘要 {@link #generateStructuredSummary}。
 *
 * <p>配置项：platform.ai.context.{max-tokens, max-messages, compression-threshold,
 * keep-recent-messages, max-message-content-length, use-llm-summary}
 */
@Service
public class ContextCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionService.class);

    private static final int MAX_MESSAGE_CONTENT_LENGTH = 4000;
    private static final int LLM_SUMMARY_TIMEOUT_SECONDS = 30;

    @Value("${platform.ai.context.max-tokens:8000}")
    private int maxTokens;

    @Value("${platform.ai.context.max-messages:50}")
    private int maxMessages;

    @Value("${platform.ai.context.compression-threshold:0.8}")
    private double compressionThreshold;

    @Value("${platform.ai.context.keep-recent-messages:3}")
    private int keepRecentMessages;

    @Value("${platform.ai.context.max-message-content-length:4000}")
    private int maxMessageContentLength;

    @Value("${platform.ai.context.use-llm-summary:true}")
    private boolean useLlmSummary;

    private final ChatModel chatModel;

    public ContextCompressionService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public CompressionResult compressIfNeeded(ChatSession session) {
        if (session == null || session.messages().isEmpty()) {
            return CompressionResult.noCompression(session);
        }

        int currentTokens = session.estimatedTokens();
        int tokenThreshold = (int) (maxTokens * compressionThreshold);

        if (currentTokens <= tokenThreshold && session.messageCount() <= maxMessages) {
            return CompressionResult.noCompression(session);
        }

        log.info("Context compression triggered: sessionId={}, currentTokens={}, messageCount={}, maxTokens={}",
                session.sessionId(), currentTokens, session.messageCount(), maxTokens);

        List<ChatMessage> compressedMessages = new ArrayList<>(session.messages());

        if (currentTokens > maxTokens) {
            log.warn("Context exceeds maxTokens={}, applying aggressive compression", maxTokens);
            compressedMessages = applyAggressiveCompression(compressedMessages);
        } else if (currentTokens > tokenThreshold) {
            compressedMessages = applySmartCompression(compressedMessages);
        }

        int compressedTokens = ChatSession.estimateTotalTokens(compressedMessages, session.systemPrompt());

        if (compressedTokens > maxTokens) {
            log.warn("Still exceeds maxTokens after compression, truncating messages");
            compressedMessages = truncateMessagesToFit(compressedMessages, maxTokens);
            compressedTokens = ChatSession.estimateTotalTokens(compressedMessages, session.systemPrompt());
        }

        compressedMessages = truncateLongMessages(compressedMessages);

        ChatSession updatedSession = session.withMessages(compressedMessages);

        log.info("Context compressed: sessionId={}, beforeTokens={}, afterTokens={}, beforeMessages={}, afterMessages={}",
                session.sessionId(), currentTokens, compressedTokens, session.messageCount(), compressedMessages.size());

        return CompressionResult.compressed(updatedSession, currentTokens, compressedTokens);
    }

    public ChatSession truncateLongMessages(ChatSession session) {
        if (session == null || session.messages().isEmpty()) {
            return session;
        }
        List<ChatMessage> truncated = truncateLongMessages(session.messages());
        return session.withMessages(truncated);
    }

    private List<ChatMessage> applySmartCompression(List<ChatMessage> messages) {
        if (messages.size() <= keepRecentMessages) {
            return new ArrayList<>(messages);
        }

        int keepFrom = Math.max(0, messages.size() - keepRecentMessages);

        List<ChatMessage> toSummarize = new ArrayList<>();
        for (int i = 0; i < keepFrom; i++) {
            toSummarize.add(messages.get(i));
        }

        String summary = generateSummaryWithFallback(toSummarize);
        List<ChatMessage> result = new ArrayList<>();
        result.add(ChatMessage.assistant("[历史对话摘要·LLM]\n" + summary));

        for (int i = keepFrom; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        return result;
    }

    /**
     * 优先用 LLM 总结；失败/超时/未启用时回退到规则提取。
     */
    private String generateSummaryWithFallback(List<ChatMessage> messages) {
        if (!useLlmSummary) {
            return generateStructuredSummary(messages);
        }
        try {
            String llmSummary = summarizeWithLLM(messages);
            if (llmSummary != null && !llmSummary.isBlank()) {
                log.info("Context summary generated by LLM: inputMsgs={}, summaryLength={}",
                        messages.size(), llmSummary.length());
                return llmSummary;
            }
        } catch (Exception e) {
            log.warn("LLM summary failed, falling back to rule-based extraction: {}", e.getMessage());
        }
        return generateStructuredSummary(messages);
    }

    /**
     * 调用 LLM 对历史消息做结构化总结，30 秒超时。
     */
    private String summarizeWithLLM(List<ChatMessage> messages) {
        String conversationText = renderConversationForLLM(messages);

        String systemPrompt = """
                你是测试平台 AI 助手的对话压缩器。请把以下多轮历史对话压缩为结构化摘要，要求：
                1. 用中文输出，不超过 600 字
                2. 按轮次列出关键信息：用户问题、调用的工具名与关键参数、助手结论
                3. 保留所有 taskId / sceneId / spaceId / 错误类型等关键标识符
                4. 省略工具返回的冗长原文，只保留结论性信息
                5. 如果对话已涉及故障定位，必须保留根因结论
                输出格式示例：
                共N轮对话;
                轮次1: 用户: ...; 工具: TaskTool(taskId=88); 结论: 任务失败于执行阶段
                轮次2: ...
                """;

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage("需要压缩的历史对话：\n\n" + conversationText)
        ));

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                chatModel.call(prompt).getResult().getOutput().getText());

        try {
            return future.get(LLM_SUMMARY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("LLM summary timed out after " + LLM_SUMMARY_TIMEOUT_SECONDS + "s");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("LLM summary failed: " + cause.getMessage(), cause);
        }
    }

    private String renderConversationForLLM(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            switch (msg.role()) {
                case "user" -> sb.append("用户: ").append(truncateForLlm(msg.content(), 800)).append("\n");
                case "assistant" -> sb.append("助手: ").append(truncateForLlm(msg.content(), 800)).append("\n");
                case "tool" -> sb.append("[").append(msg.toolName()).append("]: ")
                        .append(truncateForLlm(msg.content(), 400)).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncateForLlm(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(截断)";
    }

    private List<ChatMessage> applyAggressiveCompression(List<ChatMessage> messages) {
        if (messages.size() <= keepRecentMessages) {
            return new ArrayList<>(messages);
        }

        int keepFrom = Math.max(0, messages.size() - keepRecentMessages);

        List<ChatMessage> toSummarize = new ArrayList<>();
        for (int i = 0; i < keepFrom; i++) {
            toSummarize.add(messages.get(i));
        }

        String summary = generateAggressiveStructuredSummary(toSummarize);
        List<ChatMessage> result = new ArrayList<>();
        result.add(ChatMessage.assistant("[历史对话摘要·精简]\n" + summary));

        for (int i = keepFrom; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg.isUser() || msg.isAssistant()) {
                result.add(msg);
            }
        }

        return result;
    }

    private String generateStructuredSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();

        List<Turn> turns = groupIntoTurns(messages);
        summary.append("共").append(turns.size()).append("轮对话;\n");

        int turnLimit = Math.min(turns.size(), 8);
        for (int t = 0; t < turnLimit; t++) {
            Turn turn = turns.get(t);
            summary.append("轮次").append(t + 1).append(": ");
            summary.append("用户: ").append(trim(turn.userText(), 80));

            if (!turn.toolCalls().isEmpty()) {
                summary.append("\n  工具调用: ");
                for (int i = 0; i < Math.min(turn.toolCalls().size(), 5); i++) {
                    ToolCall tc = turn.toolCalls().get(i);
                    if (i > 0) summary.append(", ");
                    summary.append(tc.toolName()).append("(").append(trim(tc.params(), 60)).append(")");
                }
                summary.append("\n  工具结果: ");
                for (int i = 0; i < Math.min(turn.toolCalls().size(), 3); i++) {
                    ToolCall tc = turn.toolCalls().get(i);
                    if (i > 0) summary.append(", ");
                    summary.append(trim(tc.result(), 80));
                }
            }

            if (turn.assistantText() != null && !turn.assistantText().isBlank()) {
                summary.append("\n  助手结论: ").append(trim(turn.assistantText(), 100));
            }

            if (t < turnLimit - 1) {
                summary.append("\n");
            }
        }

        if (turns.size() > turnLimit) {
            summary.append("\n... 还有").append(turns.size() - turnLimit).append("轮对话已省略");
        }

        return summary.toString();
    }

    private String generateAggressiveStructuredSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();

        List<Turn> turns = groupIntoTurns(messages);
        int totalUserMsgs = (int) turns.stream().filter(t -> t.userText() != null && !t.userText().isBlank()).count();
        int totalToolCalls = turns.stream().mapToInt(t -> t.toolCalls().size()).sum();

        summary.append("对话概要: 共").append(turns.size()).append("轮, ")
                .append(totalToolCalls).append("次工具调用;\n");

        int recentTurns = Math.min(turns.size(), 3);
        if (recentTurns > 0) {
            summary.append("最近").append(recentTurns).append("轮:\n");
            int startIdx = turns.size() - recentTurns;
            for (int t = startIdx; t < turns.size(); t++) {
                Turn turn = turns.get(t);
                summary.append("- 用户: ").append(trim(turn.userText(), 60));
                if (!turn.toolCalls().isEmpty()) {
                    summary.append(" → 调用: ");
                    for (int i = 0; i < Math.min(turn.toolCalls().size(), 3); i++) {
                        if (i > 0) summary.append(", ");
                        summary.append(turn.toolCalls().get(i).toolName());
                    }
                }
                if (turn.assistantText() != null && !turn.assistantText().isBlank()) {
                    summary.append(" → ").append(trim(turn.assistantText(), 60));
                }
                summary.append("\n");
            }
        }

        return summary.toString();
    }

    private List<Turn> groupIntoTurns(List<ChatMessage> messages) {
        List<Turn> turns = new ArrayList<>();
        String currentUserText = null;
        List<ToolCall> currentToolCalls = new ArrayList<>();
        String currentAssistantText = null;
        boolean hasUser = false;

        for (ChatMessage msg : messages) {
            if (msg.isUser()) {
                if (hasUser || !currentToolCalls.isEmpty() || (currentAssistantText != null && !currentAssistantText.isBlank())) {
                    turns.add(new Turn(currentUserText, new ArrayList<>(currentToolCalls), currentAssistantText));
                    currentToolCalls = new ArrayList<>();
                    currentAssistantText = null;
                }
                currentUserText = msg.content();
                hasUser = true;
            } else if (msg.isTool()) {
                String params = extractToolParams(msg.content());
                String result = extractToolResult(msg.content());
                currentToolCalls.add(new ToolCall(msg.toolName(), params, result));
            } else if (msg.isAssistant() && hasUser) {
                if (currentAssistantText == null || currentAssistantText.isBlank()) {
                    currentAssistantText = msg.content();
                } else {
                    currentAssistantText = currentAssistantText + " " + msg.content();
                }
            }
        }

        if (hasUser || !currentToolCalls.isEmpty()) {
            turns.add(new Turn(currentUserText, currentToolCalls, currentAssistantText));
        }

        return turns;
    }

    private String extractToolParams(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String firstLine = content.split("\\r?\\n")[0].trim();
        if (firstLine.length() > 100) {
            return firstLine.substring(0, 100) + "...";
        }
        return firstLine;
    }

    private String extractToolResult(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= 150) {
            return content;
        }
        return content.substring(0, 150) + "...";
    }

    private String trim(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "(空)";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private List<ChatMessage> truncateMessagesToFit(List<ChatMessage> messages, int maxTokens) {
        List<ChatMessage> result = new ArrayList<>();
        int totalTokens = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            int msgTokens = ChatSession.estimateTextTokens(msg.content());
            if (totalTokens + msgTokens > maxTokens * 0.8) {
                break;
            }
            result.add(0, msg);
            totalTokens += msgTokens;
        }

        if (result.isEmpty() && !messages.isEmpty()) {
            result.add(messages.get(messages.size() - 1));
        }

        return result;
    }

    private List<ChatMessage> truncateLongMessages(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.content() != null && msg.content().length() > maxMessageContentLength) {
                String truncatedContent = msg.content().substring(0, maxMessageContentLength)
                        + "... [截断，原长度=" + msg.content().length() + "字符]";
                result.add(new ChatMessage(msg.role(), truncatedContent, msg.timestamp(), msg.toolName()));
            } else {
                result.add(msg);
            }
        }
        return result;
    }

    public int estimateTokensForSession(ChatSession session) {
        if (session == null || session.messages().isEmpty()) {
            return 0;
        }
        return session.estimatedTokens();
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    private record Turn(
            String userText,
            List<ToolCall> toolCalls,
            String assistantText
    ) {
    }

    private record ToolCall(
            String toolName,
            String params,
            String result
    ) {
    }

    public record CompressionResult(
            ChatSession session,
            boolean compressed,
            int originalTokens,
            int compressedTokens
    ) {
        public static CompressionResult noCompression(ChatSession session) {
            return new CompressionResult(session, false, session.estimatedTokens(), session.estimatedTokens());
        }

        public static CompressionResult compressed(ChatSession session, int originalTokens, int compressedTokens) {
            return new CompressionResult(session, true, originalTokens, compressedTokens);
        }
    }
}