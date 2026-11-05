package com.example.platform.patrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 为每次巡页准备隔离工作目录（脚本、配置、用例、视频与证据）。 */
@Component
public class PatrolWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(PatrolWorkspaceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCRIPT_RESOURCE = "patrol/patrol.js";

    private final Path workspaceRoot;
    private final Path patrolRoot;

    public PatrolWorkspaceService(@Value("${platform.runner.workspace-root:/runner-workspaces}") String root) {
        this.workspaceRoot = Paths.get(root).toAbsolutePath().normalize();
        this.patrolRoot = workspaceRoot.resolve("patrol");
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public Path prepareWorkspace(Long taskId, Map<String, Object> config) {
        Path workspace = workspaceOf(taskId);
        try {
            Files.createDirectories(workspace.resolve("screenshots"));
            Files.createDirectories(workspace.resolve("videos"));
            Files.createDirectories(workspace.resolve("evidence"));
            Files.writeString(workspace.resolve("package.json"), """
                    {
                      "name": "platform-patrol-%d",
                      "private": true,
                      "version": "1.0.0"
                    }
                    """.formatted(taskId), StandardCharsets.UTF_8);
            copyScript(workspace.resolve("patrol.js"));
            writeConfig(taskId, config);
            return workspace;
        } catch (IOException exception) {
            throw new IllegalStateException("准备巡页工作目录失败: " + exception.getMessage(), exception);
        }
    }

    public void writeConfig(Long taskId, Map<String, Object> config) {
        Path target = workspaceOf(taskId).resolve("config.json");
        try {
            Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("写入巡页配置失败: " + exception.getMessage(), exception);
        }
    }

    public void writeCases(Long taskId, Object cases) {
        Path target = workspaceOf(taskId).resolve("cases.json");
        try {
            Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(cases),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("写入巡页用例失败: " + exception.getMessage(), exception);
        }
    }

    public void removeConfig(Long taskId) {
        try {
            Files.deleteIfExists(workspaceOf(taskId).resolve("config.json"));
        } catch (IOException exception) {
            log.debug("Remove patrol config failed. taskId={}, reason={}", taskId, exception.getMessage());
        }
    }

    public Path workspaceOf(Long taskId) {
        return patrolRoot.resolve(String.valueOf(taskId));
    }

    public void cleanupWorkspace(Long taskId) {
        Path workspace = workspaceOf(taskId);
        try {
            if (!Files.exists(workspace)) {
                return;
            }
            try (var paths = Files.walk(workspace)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // 清理失败不影响任务收尾。
                    }
                });
            }
        } catch (IOException exception) {
            log.warn("Cleanup patrol workspace failed. taskId={}, reason={}", taskId, exception.getMessage());
        }
    }

    private void copyScript(Path target) throws IOException {
        ClassPathResource resource = new ClassPathResource(SCRIPT_RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Map<String, Object> config(String startUrl, String browser, boolean headless,
                                             String mode, String authType, String cookie, String token,
                                             String tokenHeader, Map<String, String> localStorage,
                                             boolean scrollScan, boolean recordVideo,
                                             boolean allowWrite, boolean allowDestructive,
                                             java.util.List<Long> approvedCaseIds,
                                             int pageTimeoutMs, int actionTimeoutMs,
                                             int visibleTextLimit, long maxDurationMs,
                                             java.util.List<java.util.Map<String, Object>> setupSteps,
                                             String evidencePrefix) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("startUrl", startUrl);
        config.put("browser", browser);
        config.put("headless", headless);
        config.put("mode", mode);
        config.put("setupSteps", setupSteps == null ? java.util.List.of() : setupSteps);
        config.put("evidencePrefix", evidencePrefix);
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("mode", authType == null ? "NONE" : authType);
        auth.put("cookie", cookie);
        auth.put("token", token);
        auth.put("tokenHeader", tokenHeader == null || tokenHeader.isBlank() ? "Authorization" : tokenHeader);
        auth.put("localStorage", localStorage == null ? Map.of() : localStorage);
        config.put("auth", auth);
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("scrollScan", scrollScan);
        limits.put("recordVideo", recordVideo);
        limits.put("allowWrite", allowWrite);
        limits.put("allowDestructive", allowDestructive);
        limits.put("approvedCaseIds", approvedCaseIds == null ? java.util.List.of() : approvedCaseIds);
        limits.put("pageTimeoutMs", pageTimeoutMs);
        limits.put("actionTimeoutMs", actionTimeoutMs);
        limits.put("visibleTextLimit", visibleTextLimit <= 0 ? 1800 : visibleTextLimit);
        limits.put("maxDurationMs", maxDurationMs);
        config.put("limits", limits);
        return config;
    }
}
