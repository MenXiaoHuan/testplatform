package com.example.platform.scene.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景服务实现类 —— 协调场景 CRUD、调度元数据管理与详情缓存生命周期。
 *
 * <p>核心职责：
 * <ul>
 *   <li>场景创建与更新：含选择器规范化、名称唯一性校验、仓库有效性校验、cron 表达式解析</li>
 *   <li>详情缓存：通过 {@link DetailCacheService} 缓存场景详情，变更时自动失效</li>
 *   <li>定时调度触发：{@link #triggerScheduledScenes} —— Spring 定时任务，扫描并触发到期场景</li>
 *   <li>分页查询：支持按空间隔离的分页查询，转换为卡片响应 DTO</li>
 * </ul>
 *
 * <p>依赖：{@link SceneMapper}、{@link TestRepositoryMapper}、
 * {@link SceneCascadeDeleteService}、{@link SceneSchedulerService}、
 * {@link DetailCacheService}、{@link ObjectMapper}。
 */
@Service
public class SceneServiceImpl implements SceneService {
    private static final Logger log = LoggerFactory.getLogger(SceneServiceImpl.class);
    private final SceneMapper sceneMapper;
    private final TestRepositoryMapper repositoryMapper;
    private final SceneCascadeDeleteService sceneCascadeDeleteService;
    private final ObjectMapper objectMapper;
    private final SceneSchedulerService sceneSchedulerService;
    private final DetailCacheService detailCacheService;
    private final SceneScheduleTimeResolver sceneScheduleTimeResolver = new SceneScheduleTimeResolver();

    public SceneServiceImpl(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            ObjectMapper objectMapper,
            SceneSchedulerService sceneSchedulerService) {
        this(sceneMapper, repositoryMapper, sceneCascadeDeleteService, objectMapper, sceneSchedulerService, null);
    }

    @Autowired
    public SceneServiceImpl(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            ObjectMapper objectMapper,
            SceneSchedulerService sceneSchedulerService,
            DetailCacheService detailCacheService) {
        this.sceneMapper = sceneMapper;
        this.repositoryMapper = repositoryMapper;
        this.sceneCascadeDeleteService = sceneCascadeDeleteService;
        this.objectMapper = objectMapper;
        this.sceneSchedulerService = sceneSchedulerService;
        this.detailCacheService = detailCacheService;
    }

    public SceneServiceImpl(
            SceneMapper sceneMapper,
            TestRepositoryMapper repositoryMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            ObjectMapper objectMapper) {
        this(sceneMapper, repositoryMapper, sceneCascadeDeleteService, objectMapper, null);
    }

    /** 创建场景，校验空间、仓库、名称唯一性等。 */
    @Override
    @Transactional
    public SceneEntity create(SceneEntity entity) {
        validateSpaceId(entity.getSpaceId());
        validateRepository(entity.getSpaceId(), entity.getRepoId());
        SceneEntity normalized = normalizeSelector(entity);
        normalized.setName(normalizeName(normalized.getName()));
        validateUniqueName(normalized.getName(), null);
        normalized.setNextRunAt(resolveNextRunAt(normalized));
        sceneMapper.insert(normalized);
        invalidateDetail(normalized.getSpaceId(), normalized.getId());
        return normalized;
    }

    /** 分页查询场景卡片列表。 */
    @Override
    public PageResponse<SceneCardResponse> listCards(Long spaceId, int page, int size) {
        validateSpaceId(spaceId);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        return PageResponse.of(
                        sceneMapper.findPageBySpaceId(spaceId, normalizedSize, offset),
                        sceneMapper.countBySpaceId(spaceId),
                        normalizedPage,
                        normalizedSize)
                .map(this::toCard);
    }

    /** 根据 ID 获取场景详情。 */
    @Override
    public SceneEntity get(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        return getOptional(spaceId, id)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }

    /** 更新场景信息，包含选择器规范化、名称唯一性校验、cron 重新解析。 */
    @Override
    @Transactional
    public SceneEntity update(Long spaceId, Long id, SceneEntity entity) {
        validateSpaceId(spaceId);
        if (entity.getSpaceId() != null && !spaceId.equals(entity.getSpaceId())) {
            throw new IllegalArgumentException("Scene space mismatch");
        }
        SceneEntity existing = get(spaceId, id);
        validateRepository(spaceId, entity.getRepoId());
        SceneEntity normalized = normalizeSelector(entity);
        String normalizedName = normalizeName(normalized.getName());
        validateUniqueName(normalizedName, id);
        existing.setSpaceId(spaceId);
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
        invalidateDetail(spaceId, id);
        return existing;
    }

    /** 删除场景（级联删除所有关联数据）。 */
    @Override
    @Transactional
    public void delete(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        get(spaceId, id);
        sceneCascadeDeleteService.deleteSceneGraph(id);
        invalidateDetail(spaceId, id);
    }

    /** 按仓库 ID 删除所有关联场景。 */
    @Override
    public void deleteAllByRepoId(Long repoId) {
        sceneMapper.deleteAllByRepoId(repoId);
    }

    private Optional<SceneEntity> getOptional(Long spaceId, Long id) {
        if (detailCacheService == null) {
            return sceneMapper.findByIdAndSpaceId(id, spaceId);
        }
        return detailCacheService.getOrLoad(detailCacheKey(spaceId), id, SceneEntity.class,
                () -> sceneMapper.findByIdAndSpaceId(id, spaceId));
    }

    private void invalidateDetail(Long spaceId, Long id) {
        if (detailCacheService != null && id != null) {
            detailCacheService.invalidate(detailCacheKey(spaceId), id);
        }
    }

    /** 每 60 秒扫描一次到期的调度场景并触发执行。 */
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

    /** 规范化选择器字段：同步 matchValue/testSelectorValue，并设置默认选择器类型。 */
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

    /** 根据 cron 表达式计算下次运行时间。 */
    private LocalDateTime resolveNextRunAt(SceneEntity entity) {
        return sceneScheduleTimeResolver.resolveNextRunAt(
                entity.getScheduleEnabled(),
                entity.getCronExpression(),
                LocalDateTime.now());
    }

    /** 校验仓库是否存在且已启用。 */
    private void validateRepository(Long spaceId, Long repoId) {
        if (repoId == null || repoId <= 0) {
            throw new IllegalArgumentException("请选择有效的所属仓库");
        }
        TestRepositoryEntity repositoryEntity = repositoryMapper.findByIdAndSpaceId(repoId, spaceId)
                .orElseThrow(() -> new IllegalArgumentException("所属仓库不存在，请重新选择"));
        if (!Boolean.TRUE.equals(repositoryEntity.getEnabled())) {
            throw new IllegalArgumentException("所属仓库已停用，请先启用仓库");
        }
    }

    /** 校验空间 ID 有效性。 */
    private void validateSpaceId(Long spaceId) {
        if (spaceId == null || spaceId <= 0) {
            throw new IllegalArgumentException("Space not found");
        }
    }

    private String detailCacheKey(Long spaceId) {
        return "scene:%d".formatted(spaceId);
    }

    /** 将实体转换为卡片响应 DTO。 */
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
