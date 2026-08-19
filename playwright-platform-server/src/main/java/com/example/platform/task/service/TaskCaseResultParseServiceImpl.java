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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 用例结果解析服务实现 —— 实现从 Playwright results.json 文件中递归解析
 * 套件、规格、测试用例的执行结果及工件绑定信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #parse(Long, Path, Path)} —— 解析 results.json 根节点，协调递归收集所有结果</li>
 *   <li>{@link #collectSuiteResults(Long, Path, JsonNode, String, List, List)} —— 递归遍历套件树，提取用例结果与附件绑定</li>
 *   <li>{@link #normalizeRelativePath(Path, String)} —— 将容器内路径转换为宿主机相对路径</li>
 *   <li>{@link #mapStatus(String)} / {@link #mapArtifactType(String, String, String)} —— 状态与工件类型映射</li>
 * </ul>
 *
 * <p>依赖：{@link ObjectMapper}（JSON 解析）、容器工作目录根路径配置。
 */
@Service
public class TaskCaseResultParseServiceImpl implements TaskCaseResultParseService {
    private final ObjectMapper objectMapper;
    private final String containerWorkspaceRoot;

    @Autowired
    public TaskCaseResultParseServiceImpl(
            ObjectMapper objectMapper,
            @Value("${platform.runner.docker.container-workspace-root:/workspace/task}") String containerWorkspaceRoot) {
        this.objectMapper = objectMapper;
        this.containerWorkspaceRoot = normalizePathString(containerWorkspaceRoot);
    }

    public TaskCaseResultParseServiceImpl(ObjectMapper objectMapper) {
        this(objectMapper, "/workspace/task");
    }

    /**
     * 解析 Playwright results.json 文件，提取所有用例结果和工件绑定信息。
     */
    @Override
    public ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot) {
        try {
            JsonNode root = objectMapper.readTree(resultsIndexFile.toFile());
            List<ParsedCaseResult> caseResults = new ArrayList<>();
            List<ParsedArtifactBinding> artifactBindings = new ArrayList<>();

            // 遍历所有顶层套件
            for (JsonNode suite : root.path("suites")) {
                collectSuiteResults(taskId, workspaceRoot, suite, null, caseResults, artifactBindings);
            }

            return new ParsedTaskResults(caseResults, artifactBindings);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse playwright results", exception);
        }
    }

    /**
     * 递归收集套件结果：遍历套件下的 specs → tests → results，提取用例状态和附件信息。
     */
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
                // 取最后一次执行的结果
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

                // 提取附件绑定关系
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

        // 递归处理子套件
        for (JsonNode childSuite : suite.path("suites")) {
            collectSuiteResults(taskId, workspaceRoot, childSuite, suiteName, caseResults, artifactBindings);
        }
    }

    /**
     * 将原始路径规范化为相对路径，处理容器内路径到宿主机路径的映射。
     */
    private String normalizeRelativePath(Path workspaceRoot, String rawPath) {
        String normalizedContainerWorkspaceRoot = normalizePathString(containerWorkspaceRoot);
        String normalizedRawPath = normalizePathString(rawPath);
        // 如果路径以容器工作目录开头，截取后面的相对部分
        if (normalizedContainerWorkspaceRoot != null
                && !normalizedContainerWorkspaceRoot.isBlank()
                && normalizedRawPath.startsWith(normalizedContainerWorkspaceRoot + "/")) {
            return normalizedRawPath.substring(normalizedContainerWorkspaceRoot.length() + 1);
        }
        Path attachmentPath = Path.of(rawPath).normalize();
        Path normalizedWorkspace = toComparablePath(workspaceRoot);
        if (attachmentPath.isAbsolute()) {
            Path comparableAttachmentPath = toComparablePath(attachmentPath);
            return normalizedWorkspace.relativize(comparableAttachmentPath).toString().replace('\\', '/');
        }
        return attachmentPath.toString().replace('\\', '/');
    }

    /**
     * 将路径转换为可比较的真实路径（解析符号链接），不可用时回退到绝对路径。
     */
    private Path toComparablePath(Path path) {
        try {
            if (Files.exists(path)) {
                return path.toRealPath().normalize();
            }
        } catch (IOException ignored) {
            // 当真实路径解析不可用时，回退到规范化的绝对路径
        }
        return path.toAbsolutePath().normalize();
    }

    /**
     * 规范化路径字符串：统一使用正斜杠，移除末尾斜杠。
     */
    private String normalizePathString(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 将 Playwright 状态映射为平台标准状态。
     */
    private String mapStatus(String status) {
        return switch (status) {
            case "passed" -> "PASSED";
            case "failed" -> "FAILED";
            case "timedOut" -> "TIMEOUT";
            default -> "SKIPPED";
        };
    }

    /**
     * 根据文件名、内容类型和路径推断工件类型。
     */
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
