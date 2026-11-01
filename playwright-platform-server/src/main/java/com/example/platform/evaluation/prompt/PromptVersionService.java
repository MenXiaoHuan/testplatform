package com.example.platform.evaluation.prompt;

import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.exception.PromptNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Prompt 版本管理 Service —— 创建 / 发布 / 废弃 / Diff。 */
@Service
public class PromptVersionService {

    private final EvalPromptVersionMapper mapper;
    private final PromptCache cache;

    public PromptVersionService(EvalPromptVersionMapper mapper, PromptCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /** 新建 DRAFT 版本（同一 promptKey + versionLabel 唯一）；新建提示词时自动生成 promptKey。 */
    public EvalPromptVersionEntity create(EvalPromptVersionEntity entity) {
        if (entity.getPromptKey() == null || entity.getPromptKey().isBlank()) {
            entity.setPromptKey(generatePromptKey(entity.getSpaceId()));
        }
        if (mapper.findByPromptKeyAndVersion(entity.getSpaceId(), entity.getPromptKey(), entity.getVersionLabel()).isPresent()) {
            throw new BusinessException("PROMPT_VERSION_CONFLICT",
                    "版本号已存在：" + entity.getPromptKey() + " / " + entity.getVersionLabel());
        }
        entity.setStatus(PromptStatus.DRAFT.name());
        if (entity.getCreatedBy() == null || entity.getCreatedBy().isBlank()) {
            entity.setCreatedBy("console");
        }
        mapper.insert(entity);
        return mapper.findById(entity.getId()).orElse(entity);
    }

    /** 编辑 DRAFT 版本内容；已发布/已废弃版本不可改。 */
    public EvalPromptVersionEntity updateDraft(Long versionId, Long spaceId, EvalPromptVersionEntity patch) {
        EvalPromptVersionEntity target = getById(versionId);
        if (!target.getSpaceId().equals(spaceId)) {
            throw new BusinessException("PROMPT_NOT_FOUND", "跨 Space 访问被拒绝");
        }
        if (!PromptStatus.DRAFT.name().equals(target.getStatus())) {
            throw new BusinessException("PROMPT_VERSION_CONFLICT", "只有 DRAFT 版本可以编辑");
        }
        target.setName(patch.getName());
        target.setDescription(patch.getDescription());
        target.setTemplate(patch.getTemplate());
        target.setVariables(patch.getVariables());
        target.setChangeLog(patch.getChangeLog());
        mapper.updateContent(target);
        return mapper.findById(versionId).orElse(target);
    }

    /** 删除任意状态的版本。 */
    public void deleteVersion(Long versionId, Long spaceId) {
        EvalPromptVersionEntity target = getById(versionId);
        if (!target.getSpaceId().equals(spaceId)) {
            throw new BusinessException("PROMPT_NOT_FOUND", "跨 Space 访问被拒绝");
        }
        mapper.deleteById(versionId);
        cache.invalidate(spaceId, target.getPromptKey());
    }

    /** 删除整个提示词（包含全部版本）。 */
    public void deletePromptGroup(Long spaceId, String promptKey) {
        if (promptKey == null || promptKey.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "promptKey 不能为空");
        }
        if (mapper.countByPromptKey(spaceId, promptKey) == 0) {
            throw new PromptNotFoundException("promptKey=" + promptKey + " 不存在");
        }
        mapper.deleteByPromptKey(spaceId, promptKey);
        cache.invalidate(spaceId, promptKey);
    }

    /** 当前 space 下所有 promptKey 的聚合摘要。 */
    public List<PromptKeySummary> listKeys(Long spaceId) {
        return mapper.listKeySummaries(spaceId);
    }

    /**
     * 发布指定版本：任意状态 → ACTIVE；若同 promptKey + spaceId 已有 ACTIVE，自动 DEPRECATED + supersededBy。
     */
    public EvalPromptVersionEntity activate(Long versionId, Long spaceId, String operator) {
        return changeStatus(versionId, spaceId, PromptStatus.ACTIVE.name());
    }

    /** 废弃版本（任意状态 → DEPRECATED）。 */
    public void deprecate(Long versionId, Long spaceId) {
        changeStatus(versionId, spaceId, PromptStatus.DEPRECATED.name());
    }

    /** 任意状态之间流转：DRAFT / ACTIVE / DEPRECATED。 */
    public EvalPromptVersionEntity changeStatus(Long versionId, Long spaceId, String rawStatus) {
        EvalPromptVersionEntity target = getById(versionId);
        if (!target.getSpaceId().equals(spaceId)) {
            throw new BusinessException("PROMPT_NOT_FOUND", "跨 Space 访问被拒绝");
        }
        PromptStatus next;
        try {
            next = PromptStatus.valueOf(rawStatus == null ? "" : rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BAD_REQUEST",
                    "status 只允许 DRAFT / ACTIVE / DEPRECATED");
        }

        if (next == PromptStatus.ACTIVE) {
            mapper.findByPromptKeyAndStatus(spaceId, target.getPromptKey(), PromptStatus.ACTIVE.name())
                    .filter(old -> !old.getId().equals(target.getId()))
                    .ifPresent(old -> mapper.updateStatus(
                            old.getId(), PromptStatus.DEPRECATED.name(), target.getId()));
        }
        mapper.updateStatus(target.getId(), next.name(), null);
        cache.invalidate(spaceId, target.getPromptKey());
        return mapper.findById(target.getId()).orElse(target);
    }

    /** 查某 promptKey 的所有版本链（version_label desc）。 */
    public List<EvalPromptVersionEntity> listVersions(Long spaceId, String promptKey) {
        return mapper.listByPromptKey(spaceId, promptKey);
    }

    /** 查某版本。 */
    public EvalPromptVersionEntity getById(Long versionId) {
        return mapper.findById(versionId)
                .orElseThrow(() -> new PromptNotFoundException("版本 id=" + versionId + " 不存在"));
    }

    /** 生成空间内唯一的 promptKey，用于 Pull API 标识。 */
    private String generatePromptKey(Long spaceId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String key = "prompt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (mapper.countByPromptKey(spaceId, key) == 0) {
                return key;
            }
        }
        throw new BusinessException("PROMPT_KEY_CONFLICT", "生成提示词唯一标识失败，请重试");
    }
}
