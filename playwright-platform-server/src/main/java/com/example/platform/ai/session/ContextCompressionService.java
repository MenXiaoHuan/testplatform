package com.example.platform.ai.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionService.class);

    @Value("${platform.ai.context.max-tokens:8000}")
    private int maxTokens;

    @Value("${platform.ai.context.max-messages:50}")
    private int maxMessages;

    @Value("${platform.ai.context.compression-threshold:0.8}")
    private double compressionThreshold;

    @Value("${platform.ai.context.keep-recent-messages:10}")
    private int keepRecentMessages;

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

        List<ChatMessage> compressed = applySlidingWindow(session.messages());
        int compressedTokens = estimateTokens(compressed);

        if (compressedTokens > tokenThreshold) {
            compressed = applySummaryCompression(compressed);
            compressedTokens = estimateTokens(compressed);
        }

        ChatSession updatedSession = session.withMessages(compressed);

        log.info("Context compressed: sessionId={}, beforeTokens={}, afterTokens={}, beforeMessages={}, afterMessages={}",
                session.sessionId(), currentTokens, compressedTokens, session.messageCount(), compressed.size());

        return CompressionResult.compressed(updatedSession, currentTokens, compressedTokens);
    }

    private List<ChatMessage> applySlidingWindow(List<ChatMessage> messages) {
        if (messages.size() <= keepRecentMessages) {
            return new ArrayList<>(messages);
        }

        List<ChatMessage> result = new ArrayList<>();
        int startIdx = messages.size() - keepRecentMessages;

        for (int i = startIdx; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        return result;
    }

    private List<ChatMessage> applySummaryCompression(List<ChatMessage> messages) {
        if (messages.size() <= keepRecentMessages) {
            return new ArrayList<>(messages);
        }

        List<ChatMessage> result = new ArrayList<>();

        int keepFrom = Math.max(keepRecentMessages / 2, 4);
        List<ChatMessage> toSummarize = new ArrayList<>();

        for (int i = 0; i < messages.size() - keepFrom; i++) {
            toSummarize.add(messages.get(i));
        }

        String summary = generateHeuristicSummary(toSummarize);
        result.add(ChatMessage.assistant("[摘要] " + summary));

        for (int i = messages.size() - keepFrom; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        return result;
    }

    private String generateHeuristicSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        int pointCount = 0;
        int maxPoints = 5;

        for (ChatMessage msg : messages) {
            if (pointCount >= maxPoints) {
                break;
            }
            if (msg.isUser() && msg.content() != null && !msg.content().isBlank()) {
                String trimmed = msg.content().length() > 100
                        ? msg.content().substring(0, 100) + "..."
                        : msg.content();
                summary.append("用户提问").append(pointCount + 1).append(": ").append(trimmed).append("; ");
                pointCount++;
            } else if (msg.isTool() && msg.content() != null) {
                summary.append("工具[").append(msg.toolName()).append("]已调用; ");
                pointCount++;
            }
        }

        if (summary.length() == 0) {
            summary.append("对话历史较早内容; ");
        }

        return summary.toString();
    }

    private int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += estimateTokensForText(msg.content());
        }
        return total;
    }

    private int estimateTokensForText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
    }

    public int estimateTokensForSession(ChatSession session) {
        if (session == null || session.messages().isEmpty()) {
            return 0;
        }
        return estimateTokens(session.messages());
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