package com.example.platform.ai.tools;

import com.example.platform.scene.service.SceneService;
import com.example.platform.scene.model.SceneEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SceneTool {

    private static final Logger log = LoggerFactory.getLogger(SceneTool.class);

    private final SceneService sceneService;

    public SceneTool(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @Tool(description = "List test scenes scoped to the current space, optionally filtered by repository ID")
    public String listScenes(
            @ToolParam(description = "Repository ID to filter by (optional)") Long repoId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: listScenes, repoId={}, spaceId={}", repoId, spaceId);
        try {
            var scenes = sceneService.listCards(spaceId, 1, 50);
            List<String> filtered = scenes.items().stream()
                    .filter(s -> repoId == null || repoId.equals(s.repoId()))
                    .map(s -> String.format("- [ID:%d] %s (repoId:%d, status:%s, schedule:%s)%s",
                            s.id(), s.name(), s.repoId(), s.lastTaskStatus(),
                            s.scheduleEnabled() ? "enabled" : "disabled",
                            s.description() != null && !s.description().isBlank() ? " - " + s.description() : ""))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                return "No scenes found" + (repoId != null ? " for repo " + repoId : "") + " in space " + spaceId;
            }
            return "Found " + filtered.size() + " scenes in space " + spaceId + ":\n" + String.join("\n", filtered);
        } catch (Exception e) {
            log.error("listScenes failed", e);
            return "Error listing scenes: " + e.getMessage();
        }
    }

    @Tool(description = "Get detailed information about a specific scene by ID within the current space")
    public String getSceneDetail(
            @ToolParam(description = "Scene ID") Long sceneId,
            @ToolParam(description = "Space ID for data isolation - always required") Long spaceId) {
        log.info("AI tool: getSceneDetail, id={}, spaceId={}", sceneId, spaceId);
        try {
            SceneEntity scene = sceneService.get(spaceId, sceneId);
            if (scene == null) {
                return "Scene not found: " + sceneId + " in space " + spaceId;
            }
            return String.format("Scene [ID:%d]: name=%s, repoId=%d, branch=%s, selector=%s=%s, browser=%s, " +
                            "schedule=%s, cron=%s, lastStatus=%s, lastRun=%s, envCount=%d",
                    scene.getId(), scene.getName(), scene.getRepoId(), scene.getBranch(),
                    scene.getTestSelectorType(), scene.getTestSelectorValue(), scene.getBrowser(),
                    Boolean.TRUE.equals(scene.getScheduleEnabled()) ? "enabled" : "disabled",
                    scene.getCronExpression(), scene.getLastTaskStatus(), scene.getLastRunAt(),
                    scene.getEnvJson() != null ? countEnvVars(scene.getEnvJson()) : 0);
        } catch (Exception e) {
            log.error("getSceneDetail failed", e);
            return "Error getting scene: " + e.getMessage();
        }
    }

    private int countEnvVars(String envJson) {
        if (envJson == null || envJson.isBlank()) {
            return 0;
        }
        try {
            String json = envJson.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                String inner = json.substring(1, json.length() - 1);
                if (inner.isBlank()) return 0;
                return inner.split(",").length;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
