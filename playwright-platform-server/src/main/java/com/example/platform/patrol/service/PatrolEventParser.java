package com.example.platform.patrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 巡页事件解析：把 Runner 写出的 events.jsonl 还原为入口页快照、用例结果与步骤结果。
 *
 * <p>事件：PAGE_SNAPSHOT / CASE_STEP / CASE_END / RUN_END。
 */
@Component
public class PatrolEventParser {
    private static final Logger log = LoggerFactory.getLogger(PatrolEventParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParseResult parse(Path eventsFile) {
        if (eventsFile == null || !Files.exists(eventsFile)) {
            return ParseResult.empty();
        }
        Snapshot snapshot = null;
        List<StepRun> steps = new ArrayList<>();
        List<CaseRun> cases = new ArrayList<>();
        String runStatus = null;
        String runReason = null;
        String setupFailure = null;
        try {
            for (String line : Files.readAllLines(eventsFile, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                JsonNode event;
                try {
                    event = MAPPER.readTree(line);
                } catch (IOException exception) {
                    continue;
                }
                String type = event.path("type").asText("");
                switch (type) {
                    case "PAGE_SNAPSHOT" -> {
                        JsonNode page = event.path("page");
                        JsonNode snapshotNode = event.path("snapshot");
                        String visibleText = snapshotNode.path("visibleText").asText("");
                        int visibleTextLength = snapshotNode.path("visibleTextLength")
                                .asInt(visibleText.length());
                        boolean visibleTextTruncated = snapshotNode.path("visibleTextTruncated")
                                .asBoolean(false) || visibleTextLength > visibleText.length();
                        snapshot = new Snapshot(
                                page.path("url").asText(""),
                                page.path("title").asText(""),
                                visibleText,
                                snapshotNode.path("elements").toString(),
                                event.path("screenshot").asText(null),
                                page.path("consoleErrors").asInt(0),
                                visibleTextLength,
                                visibleTextTruncated);
                    }
                    case "CASE_STEP" -> steps.add(new StepRun(
                            event.path("caseId").asLong(),
                            event.path("seq").asInt(),
                            event.path("status").asText(""),
                            event.path("message").asText(""),
                            event.path("screenshot").asText(null),
                            event.path("request").asText(null),
                            event.path("approved").asBoolean(false),
                            event.path("durationMs").isNumber() ? event.path("durationMs").asLong() : null));
                    case "CASE_END" -> {
                        String visibleText = event.path("visibleText").asText("");
                        int visibleTextLength = event.path("visibleTextLength")
                                .asInt(visibleText.length());
                        boolean visibleTextTruncated = event.path("visibleTextTruncated")
                                .asBoolean(false) || visibleTextLength > visibleText.length();
                        cases.add(new CaseRun(
                                event.path("caseId").asLong(),
                                event.path("status").asText(""),
                                event.path("message").asText(""),
                                event.path("video").asText(null),
                                event.path("console").asText(null),
                                event.path("dom").asText(null),
                                event.path("screenshot").asText(null),
                                visibleText,
                                event.path("durationMs").isNumber() ? event.path("durationMs").asLong() : null,
                                event.path("consoleErrors").asInt(0),
                                visibleTextLength,
                                visibleTextTruncated));
                    }
                    case "RUN_END" -> {
                        runStatus = event.path("status").asText(null);
                        runReason = event.path("reason").asText(null);
                    }
                    case "SETUP_FAILED" -> setupFailure = event.path("message").asText("前置执行失败");
                    default -> {
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("Patrol events parse failed: {}", exception.getMessage());
        }
        return new ParseResult(snapshot, steps, cases, runStatus, runReason, setupFailure);
    }

    public record Snapshot(String url, String title, String visibleText, String elementsJson,
                           String screenshot, int consoleErrors,
                           int visibleTextLength, boolean visibleTextTruncated) {
    }

    public record CaseRun(long caseId, String status, String message, String video,
                          String consoleKey, String domKey, String screenshot, String visibleText,
                          Long durationMs, int consoleErrors,
                          int visibleTextLength, boolean visibleTextTruncated) {
    }

    public record StepRun(long caseId, int seq, String status, String message, String screenshot,
                          String requestJson, boolean approved, Long durationMs) {
    }

    public record ParseResult(Snapshot snapshot, List<StepRun> steps, List<CaseRun> cases,
                              String runStatus, String runReason, String setupFailure) {
        public static ParseResult empty() {
            return new ParseResult(null, List.of(), List.of(), null, null, null);
        }
    }
}
