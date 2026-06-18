package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class SceneServiceImpl implements SceneService {
    private static final Logger log = LoggerFactory.getLogger(SceneServiceImpl.class);
    private final SceneMapper sceneMapper;
    private final TestRepositoryMapper repositoryMapper;
    private final SceneCascadeDeleteService sceneCascadeDeleteService;
    private final ObjectMapper objectMapper;
    private final SceneSchedulerService sceneSchedulerService;
    private final SceneScheduleTimeResolver sceneScheduleTimeResolver = new SceneScheduleTimeResolver();

    @Autowired
    public SceneServiceImpl(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            ObjectMapper objectMapper,
            SceneSchedulerService sceneSchedulerService) {
        this.sceneMapper = sceneMapper;
        this.repositoryMapper = repositoryMapper;
        this.sceneCascadeDeleteService = sceneCascadeDeleteService;
        this.objectMapper = objectMapper;
        this.sceneSchedulerService = sceneSchedulerService;
    }

    public SceneServiceImpl(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            ObjectMapper objectMapper) {
        this(sceneMapper, repositoryMapper, sceneCascadeDeleteService, objectMapper, null);
    }

    @Override
    public SceneEntity create(SceneEntity entity) {
        validateRepository(entity.getRepoId());
        SceneEntity normalized = normalizeSelector(entity);
        normalized.setName(normalizeName(normalized.getName()));
        validateUniqueName(normalized.getName(), null);
        normalized.setNextRunAt(resolveNextRunAt(normalized));
        sceneMapper.insert(normalized);
        return normalized;
    }

    @Override
    public PageResponse<SceneCardResponse> listCards(int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        return PageResponse.of(
                        sceneMapper.findPage(normalizedSize, offset),
                        sceneMapper.countAll(),
                        normalizedPage,
                        normalizedSize)
                .map(this::toCard);
    }

    @Override
    public SceneEntity get(Long id) {
        return sceneMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }

    @Override
    public SceneEntity update(Long id, SceneEntity entity) {
        SceneEntity existing = get(id);
        validateRepository(entity.getRepoId());
        SceneEntity normalized = normalizeSelector(entity);
        String normalizedName = normalizeName(normalized.getName());
        validateUniqueName(normalizedName, id);
        existing.setRepoId(entity.getRepoId());
        existing.setName(normalizedName);
        existing.setDescription(entity.getDescription());
        existing.setBranch(entity.getBranch());
        existing.setTestSelectorType(normalized.getTestSelectorType());
        existing.setTestSelectorValue(normalized.getTestSelectorValue());
        existing.setMatchValue(normalized.getMatchValue());
        existing.setProjectName(entity.getProjectName());
        existing.setBrowser(entity.getBrowser());
        existing.setEnvJson(entity.getEnvJson());
        existing.setRunCommand(entity.getRunCommand());
        existing.setScheduleEnabled(entity.getScheduleEnabled());
        existing.setCronExpression(entity.getCronExpression());
        existing.setNextRunAt(resolveNextRunAt(existing));
        sceneMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        sceneCascadeDeleteService.deleteSceneGraph(id);
    }

    @Override
    public void deleteAllByRepoId(Long repoId) {
        sceneMapper.deleteAllByRepoId(repoId);
    }

    @Scheduled(fixedDelay = 60000)
    public void triggerScheduledScenes() {
        if (sceneSchedulerService == null) {
            sceneMapper.findAllByScheduleEnabledTrue().stream()
                    .filter(scene -> scene.getCronExpression() != null && !scene.getCronExpression().isBlank())
                    .forEach(scene -> log.info(
                            "Scheduling hook scanned scene id={}, cron={}",
                            scene.getId(),
                            scene.getCronExpression()));
            return;
        }
        sceneSchedulerService.triggerDueScenes(java.time.LocalDateTime.now());
    }

    private SceneEntity normalizeSelector(SceneEntity entity) {
        String matchValue = entity.getMatchValue();
        String selectorValue = entity.getTestSelectorValue();

        if ((selectorValue == null || selectorValue.isBlank()) && matchValue != null && !matchValue.isBlank()) {
            entity.setTestSelectorValue(matchValue);
        } else if ((matchValue == null || matchValue.isBlank()) && selectorValue != null && !selectorValue.isBlank()) {
            entity.setMatchValue(selectorValue);
        }

        if (entity.getTestSelectorType() == null || entity.getTestSelectorType().isBlank()) {
            entity.setTestSelectorType("file");
        }

        if (entity.getEnvJson() != null && entity.getEnvJson().isBlank()) {
            entity.setEnvJson(null);
        }

        if (entity.getCronExpression() != null && entity.getCronExpression().isBlank()) {
            entity.setCronExpression(null);
        }

        return entity;
    }

    private LocalDateTime resolveNextRunAt(SceneEntity entity) {
        return sceneScheduleTimeResolver.resolveNextRunAt(
                entity.getScheduleEnabled(),
                entity.getCronExpression(),
                LocalDateTime.now());
    }

    private void validateRepository(Long repoId) {
        if (repoId == null || repoId <= 0) {
            throw new IllegalArgumentException("请选择有效的所属仓库");
        }
        TestRepositoryEntity repositoryEntity = repositoryMapper.findById(repoId)
                .orElseThrow(() -> new IllegalArgumentException("所属仓库不存在，请重新选择"));
        if (!Boolean.TRUE.equals(repositoryEntity.getEnabled())) {
            throw new IllegalArgumentException("所属仓库已停用，请先启用仓库");
        }
    }

    private SceneCardResponse toCard(SceneEntity scene) {
        return new SceneCardResponse(
                scene.getId(),
                scene.getRepoId(),
                scene.getName(),
                scene.getDescription(),
                scene.getBranch(),
                Boolean.TRUE.equals(scene.getScheduleEnabled()),
                scene.getCronExpression(),
                scene.getLastTaskStatus(),
                scene.getLastRunAt(),
                countEnvironmentVariables(scene.getEnvJson()));
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private int countEnvironmentVariables(String envJson) {
        if (envJson == null || envJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(envJson);
            return root.isObject() ? root.size() : 0;
        } catch (Exception exception) {
            return 0;
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入场景名称");
        }
        return normalized;
    }

    private void validateUniqueName(String name, Long currentId) {
        boolean duplicated = currentId == null
                ? sceneMapper.existsByNameIgnoreCase(name)
                : sceneMapper.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (duplicated) {
            throw new IllegalStateException("场景名称已存在，请更换后重试");
        }
    }
}
