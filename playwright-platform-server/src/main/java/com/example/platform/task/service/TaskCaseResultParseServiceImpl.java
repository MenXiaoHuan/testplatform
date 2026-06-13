package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedCaseResult;
import com.example.platform.task.parser.ParsedTaskResults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskCaseResultParseServiceImpl implements TaskCaseResultParseService {
    private final ObjectMapper objectMapper;

    public TaskCaseResultParseServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot) {
        try {
            JsonNode root = objectMapper.readTree(resultsIndexFile.toFile());
            List<ParsedCaseResult> caseResults = new ArrayList<>();
            List<ParsedArtifactBinding> artifactBindings = new ArrayList<>();

            for (JsonNode suite : root.path("suites")) {
                collectSuiteResults(taskId, workspaceRoot, suite, null, caseResults, artifactBindings);
            }

            return new ParsedTaskResults(caseResults, artifactBindings);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse playwright results", exception);
        }
    }

    private void collectSuiteResults(
            Long taskId,
            Path workspaceRoot,
            JsonNode suite,
            String parentSuiteName,
            List<ParsedCaseResult> caseResults,
            List<ParsedArtifactBinding> artifactBindings) {
        String suiteTitle = suite.path("title").asText();
        String suiteName = (parentSuiteName == null || parentSuiteName.isBlank())
                ? suiteTitle
                : parentSuiteName + " / " + suiteTitle;

        for (JsonNode spec : suite.path("specs")) {
            String storyName = spec.path("title").asText();
            String fullName = suiteName + " :: " + storyName;
            for (JsonNode test : spec.path("tests")) {
                String projectName = test.path("projectName").asText();
                JsonNode results = test.path("results");
                if (!results.isArray() || results.isEmpty()) {
                    continue;
                }
                JsonNode result = results.get(results.size() - 1);
                String historyId = projectName + "::" + suiteName + "::" + storyName;
                caseResults.add(new ParsedCaseResult(
                        taskId,
                        historyId,
                        fullName,
                        suiteName,
                        storyName,
                        mapStatus(result.path("status").asText()),
                        result.path("duration").asLong(),
                        projectName));

                for (JsonNode attachment : result.path("attachments")) {
                    String rawPath = attachment.path("path").asText();
                    if (rawPath == null || rawPath.isBlank()) {
                        continue;
                    }
                    artifactBindings.add(new ParsedArtifactBinding(
                            normalizeRelativePath(workspaceRoot, rawPath),
                            mapArtifactType(
                                    attachment.path("name").asText(),
                                    attachment.path("contentType").asText(),
                                    rawPath),
                            historyId));
                }
            }
        }

        for (JsonNode childSuite : suite.path("suites")) {
            collectSuiteResults(taskId, workspaceRoot, childSuite, suiteName, caseResults, artifactBindings);
        }
    }

    private String normalizeRelativePath(Path workspaceRoot, String rawPath) {
        Path attachmentPath = Path.of(rawPath).normalize();
        Path normalizedWorkspace = toComparablePath(workspaceRoot);
        if (attachmentPath.isAbsolute()) {
            Path comparableAttachmentPath = toComparablePath(attachmentPath);
            return normalizedWorkspace.relativize(comparableAttachmentPath).toString().replace('\\', '/');
        }
        return attachmentPath.toString().replace('\\', '/');
    }

    private Path toComparablePath(Path path) {
        try {
            if (Files.exists(path)) {
                return path.toRealPath().normalize();
            }
        } catch (IOException ignored) {
            // Fall back to normalized absolute path when real path resolution is unavailable.
        }
        return path.toAbsolutePath().normalize();
    }

    private String mapStatus(String status) {
        return switch (status) {
            case "passed" -> "PASSED";
            case "failed" -> "FAILED";
            case "timedOut" -> "TIMEOUT";
            default -> "SKIPPED";
        };
    }

    private String mapArtifactType(String name, String contentType, String path) {
        String lowerName = name == null ? "" : name.toLowerCase();
        String lowerType = contentType == null ? "" : contentType.toLowerCase();
        String lowerPath = path == null ? "" : path.toLowerCase();
        if (lowerName.contains("trace") || lowerPath.endsWith(".zip")) {
            return "TRACE";
        }
        if (lowerType.startsWith("video/") || lowerPath.endsWith(".webm")) {
            return "VIDEO";
        }
        if (lowerType.startsWith("image/") || lowerPath.endsWith(".png")) {
            return "SCREENSHOT";
        }
        return "REPORT_FILE";
    }
}
