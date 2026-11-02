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
                        snapshot = new Snapshot(
                                page.path("url").asText(""),
                                page.path("title").asText(""),
                                snapshotNode.path("visibleText").asText(""),
                                snapshotNode.path("elements").toString(),
                                event.path("screenshot").asText(null),
                                page.path("consoleErrors").asInt(0));
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
                    case "CASE_END" -> cases.add(new CaseRun(
                            event.path("caseId").asLong(),
                            event.path("status").asText(""),
                            event.path("message").asText(""),
                            event.path("video").asText(null),
                            event.path("console").asText(null),
                            event.path("dom").asText(null),
                            event.path("screenshot").asText(null),
                            event.path("visibleText").asText(""),
                            event.path("durationMs").isNumber() ? event.path("durationMs").asLong() : null,
                            event.path("consoleErrors").asInt(0)));
                    case "RUN_END" -> {
                        runStatus = event.path("status").asText(null);
                        runReason = event.path("reason").asText(null);
                    }
                    default -> {
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("Patrol events parse failed: {}", exception.getMessage());
        }
        return new ParseResult(snapshot, steps, cases, runStatus, runReason);
    }

    public record Snapshot(String url, String title, String visibleText, String elementsJson,
                           String screenshot, int consoleErrors) {
    }

    public record CaseRun(long caseId, String status, String message, String video,
                          String consoleKey, String domKey, String screenshot, String visibleText,
                          Long durationMs, int consoleErrors) {
    }

    public record StepRun(long caseId, int seq, String status, String message, String screenshot,
                          String requestJson, boolean approved, Long durationMs) {
    }

    public record ParseResult(Snapshot snapshot, List<StepRun> steps, List<CaseRun> cases,
                              String runStatus, String runReason) {
        public static ParseResult empty() {
            return new ParseResult(null, List.of(), List.of(), null, null);
        }
    }
}
