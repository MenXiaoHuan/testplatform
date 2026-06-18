package com.example.platform.task.service;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.CaseArtifactLinkResponse;
import com.example.platform.task.dto.CaseResultResponse;
import com.example.platform.task.dto.SceneTaskListResponse;
import com.example.platform.task.dto.TaskDetailResponse;
import com.example.platform.task.dto.TaskStageLogResponse;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TaskQueryViewService {
    private static final Logger log = LoggerFactory.getLogger(TaskQueryViewService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Field TASK_CREATED_AT_FIELD = initTaskCreatedAtField();

    private final SceneMapper sceneMapper;
    private final TestRepositoryMapper repositoryMapper;
    private final CaseResultMapper caseResultRepository;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    TaskQueryViewService(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            CaseResultMapper caseResultRepository,
            ObjectStorageService objectStorageService,
            String storageBucket) {
        this.sceneMapper = sceneMapper;
        this.repositoryMapper = repositoryMapper;
        this.caseResultRepository = caseResultRepository;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    SceneTaskListResponse toSceneTaskListResponse(TaskEntity task) {
        TaskEntity summarizedTask = withTaskSummary(task);
        return SceneTaskListResponse.from(summarizedTask);
    }

    TaskDetailResponse toTaskDetailResponse(TaskEntity task, int artifactCount) {
        String sceneName = sceneMapper.findById(task.getSceneId())
                .map(SceneEntity::getName)
                .orElse(null);
        String repositoryName = repositoryMapper.findById(task.getRepoId())
                .map(TestRepositoryEntity::getName)
                .orElse(null);
        return TaskDetailResponse.from(
                task,
                sceneName,
                repositoryName,
                countEnvironmentVariables(task.getResolvedEnvJson()),
                artifactCount);
    }

    CaseResultResponse toCaseResultResponse(CaseResultEntity caseResult, List<ArtifactEntity> caseArtifacts) {
        List<CaseArtifactLinkResponse> artifactLinks = toCaseArtifactLinks(caseArtifacts);
        return CaseResultResponse.from(
                caseResult,
                null,
                firstArtifactUrl(caseArtifacts, "VIDEO"),
                firstArtifactUrl(caseArtifacts, "TRACE"),
                artifactUrls(caseArtifacts, "SCREENSHOT"),
                firstArtifactUrl(caseArtifacts, "LOG"),
                artifactLinks.size(),
                artifactLinks);
    }

    ArtifactEntity withAccessibleArtifactUrl(ArtifactEntity artifact) {
        ArtifactEntity copy = copyArtifact(artifact);
        if (artifact.getBucket() != null && artifact.getObjectKey() != null) {
            copy.setUrl(safePresignedUrl(artifact.getBucket(), artifact.getObjectKey(), artifact.getUrl()));
        }
        return copy;
    }

    TaskStageLogResponse toTaskStageLogResponse(TaskStageLogEntity logEntity) {
        return TaskStageLogResponse.from(
                logEntity,
                safePresignedUrl(storageBucket, logEntity.getObjectKey(), logEntity.getObjectKey()));
    }

    private String safePresignedUrl(String bucket, String objectKeyOrUrl, String fallbackUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return fallbackUrl;
        }
        try {
            return objectStorageService.createPresignedGetUrl(bucket, objectKeyOrUrl);
        } catch (IllegalStateException exception) {
            log.warn(
                    "Failed to create presigned url, fallback to stored url. bucket={}, source={}, reason={}",
                    bucket,
                    objectKeyOrUrl,
                    exception.getMessage());
            return fallbackUrl != null && !fallbackUrl.isBlank() ? fallbackUrl : objectKeyOrUrl;
        }
    }

    private TaskEntity withTaskSummary(TaskEntity task) {
        TaskEntity copy = copyTask(task);
        List<CaseResultEntity> caseResults = caseResultRepository.findAllByTaskIdOrderByIdAsc(task.getId());
        copy.setPassedCount((int) caseResults.stream()
                .filter(result -> "PASSED".equalsIgnoreCase(result.getStatus()))
                .count());
        copy.setFailedCount((int) caseResults.stream()
                .filter(result -> "FAILED".equalsIgnoreCase(result.getStatus()))
                .count());
        copy.setSkippedCount((int) caseResults.stream()
                .filter(result -> "SKIPPED".equalsIgnoreCase(result.getStatus()))
                .count());
        return copy;
    }

    private List<CaseArtifactLinkResponse> toCaseArtifactLinks(List<ArtifactEntity> artifacts) {
        return artifacts.stream()
                .map(artifact -> {
                    String normalizedType = normalizeArtifactType(artifact.getArtifactType());
                    if (normalizedType == null || artifact.getUrl() == null || artifact.getUrl().isBlank()) {
                        return null;
                    }
                    return new CaseArtifactLinkResponse(
                            normalizedType,
                            normalizedType.toLowerCase(Locale.ROOT),
                            artifact.getCaseResultId() == null ? "TASK" : "CASE",
                            artifact.getUrl());
                })
                .filter(link -> link != null)
                .collect(Collectors.toMap(
                        CaseArtifactLinkResponse::artifactType,
                        link -> link,
                        (left, right) -> left))
                .values()
                .stream()
                .toList();
    }

    private String normalizeArtifactType(String artifactType) {
        if (artifactType == null) {
            return null;
        }
        String normalized = artifactType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TRACE", "VIDEO", "SCREENSHOT", "LOG" -> normalized;
            default -> "OTHER";
        };
    }

    private String firstArtifactUrl(List<ArtifactEntity> artifacts, String artifactType) {
        return artifacts.stream()
                .filter(artifact -> artifactType.equals(normalizeArtifactType(artifact.getArtifactType())))
                .map(ArtifactEntity::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<String> artifactUrls(List<ArtifactEntity> artifacts, String artifactType) {
        return artifacts.stream()
                .filter(artifact -> artifactType.equals(normalizeArtifactType(artifact.getArtifactType())))
                .map(ArtifactEntity::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    private int countEnvironmentVariables(String envJson) {
        if (envJson == null || envJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(envJson);
            return root.isObject() ? root.size() : 0;
        } catch (Exception exception) {
            return 0;
        }
    }

    private TaskEntity copyTask(TaskEntity source) {
        TaskEntity copy = new TaskEntity();
        copy.setId(source.getId());
        copy.setSceneId(source.getSceneId());
        copy.setRepoId(source.getRepoId());
        copy.setStatus(source.getStatus());
        copy.setTriggerType(source.getTriggerType());
        copy.setTriggerUser(source.getTriggerUser());
        copy.setTriggerReason(source.getTriggerReason());
        copy.setQueuedAt(source.getQueuedAt());
        copy.setBranch(source.getBranch());
        copy.setCommitSha(source.getCommitSha());
        copy.setStartedAt(source.getStartedAt());
        copy.setFinishedAt(source.getFinishedAt());
        copy.setDurationMs(source.getDurationMs());
        copy.setRunnerName(source.getRunnerName());
        copy.setCurrentStage(source.getCurrentStage());
        copy.setResultCode(source.getResultCode());
        copy.setResultMessage(source.getResultMessage());
        copy.setCancelRequested(source.getCancelRequested());
        copy.setCancelRequestedAt(source.getCancelRequestedAt());
        copy.setCancelRequestedBy(source.getCancelRequestedBy());
        copy.setLogUrl(source.getLogUrl());
        copy.setResolvedBranch(source.getResolvedBranch());
        copy.setResolvedBrowser(source.getResolvedBrowser());
        copy.setResolvedEnvJson(source.getResolvedEnvJson());
        copy.setResolvedMatchValue(source.getResolvedMatchValue());
        copy.setResolvedTestRoot(source.getResolvedTestRoot());
        copy.setResolvedRunCommand(source.getResolvedRunCommand());
        copy.setPassedCount(source.getPassedCount());
        copy.setFailedCount(source.getFailedCount());
        copy.setSkippedCount(source.getSkippedCount());
        copyReadOnlyCreatedAt(source, copy);
        return copy;
    }

    private ArtifactEntity copyArtifact(ArtifactEntity source) {
        ArtifactEntity copy = new ArtifactEntity();
        copy.setId(source.getId());
        copy.setTaskId(source.getTaskId());
        copy.setCaseResultId(source.getCaseResultId());
        copy.setArtifactType(source.getArtifactType());
        copy.setBucket(source.getBucket());
        copy.setObjectKey(source.getObjectKey());
        copy.setContentType(source.getContentType());
        copy.setSize(source.getSize());
        copy.setUrl(source.getUrl());
        return copy;
    }

    private static Field initTaskCreatedAtField() {
        try {
            Field field = TaskEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access TaskEntity.createdAt", exception);
        }
    }

    private void copyReadOnlyCreatedAt(TaskEntity source, TaskEntity target) {
        try {
            TASK_CREATED_AT_FIELD.set(target, source.getCreatedAt());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to copy TaskEntity.createdAt", exception);
        }
    }
}
