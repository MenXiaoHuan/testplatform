package com.example.platform.repository.service;

import com.example.platform.cache.DetailCacheService;
import com.example.platform.common.CredentialCipher;
import com.example.platform.common.PageResponse;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.runner.service.GitUrlPolicy;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles repository CRUD with short write transactions and detail-cache invalidation.
 */
@Service
public class RepositoryServiceImpl implements RepositoryService {
    private static final String AUTH_PUBLIC = "PUBLIC";
    private static final String AUTH_HTTPS_TOKEN = "HTTPS_TOKEN";
    private static final String AUTH_SSH_KEY = "SSH_KEY";
    private static final Pattern SCP_GIT_URL = Pattern.compile("^[\\w.-]+@[\\w.-]+:.+$");

    private final TestRepositoryMapper repository;
    private final RepositoryCascadeDeleteService repositoryCascadeDeleteService;
    private final DetailCacheService detailCacheService;
    private final CredentialCipher credentialCipher;

    @Autowired
    public RepositoryServiceImpl(
            TestRepositoryMapper repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService,
            DetailCacheService detailCacheService,
            CredentialCipher credentialCipher) {
        this.repository = repository;
        this.repositoryCascadeDeleteService = repositoryCascadeDeleteService;
        this.detailCacheService = detailCacheService;
        this.credentialCipher = credentialCipher;
    }

    public RepositoryServiceImpl(
            TestRepositoryMapper repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService,
            DetailCacheService detailCacheService) {
        this(repository, repositoryCascadeDeleteService, detailCacheService, new CredentialCipher(""));
    }

    public RepositoryServiceImpl(
            TestRepositoryMapper repository,
            RepositoryCascadeDeleteService repositoryCascadeDeleteService) {
        this(repository, repositoryCascadeDeleteService, null, new CredentialCipher(""));
    }

    @Override
    @Transactional
    public TestRepositoryEntity create(TestRepositoryEntity entity) {
        if (entity.getSpaceId() == null || entity.getSpaceId() <= 0) {
            throw new IllegalArgumentException("Repository space is required");
        }
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, null);
        GitUrlPolicy.validate(entity.getGitUrl());
        entity.setName(normalizedName);
        applyCredentials(entity, entity, null);
        repository.insert(entity);
        invalidateDetail(entity.getSpaceId(), entity.getId());
        return entity;
    }

    @Override
    public PageResponse<TestRepositoryEntity> list(Long spaceId, int page, int size) {
        validateSpaceId(spaceId);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        return PageResponse.of(
                repository.findPageBySpaceId(spaceId, normalizedSize, offset),
                repository.countBySpaceId(spaceId),
                normalizedPage,
                normalizedSize);
    }

    @Override
    public TestRepositoryEntity get(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        return getOptional(spaceId, id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Override
    @Transactional
    public TestRepositoryEntity update(Long spaceId, Long id, TestRepositoryEntity entity) {
        validateSpaceId(spaceId);
        if (entity.getSpaceId() != null && !spaceId.equals(entity.getSpaceId())) {
            throw new IllegalArgumentException("Repository space mismatch");
        }
        TestRepositoryEntity existing = get(spaceId, id);
        String normalizedName = normalizeName(entity.getName());
        validateUniqueName(normalizedName, id);
        GitUrlPolicy.validate(entity.getGitUrl());
        existing.setSpaceId(spaceId);
        existing.setName(normalizedName);
        existing.setGitUrl(entity.getGitUrl());
        existing.setDefaultBranch(entity.getDefaultBranch());
        existing.setWorkingDirectory(entity.getWorkingDirectory());
        existing.setInstallCommand(entity.getInstallCommand());
        existing.setRunCommandTemplate(entity.getRunCommandTemplate());
        existing.setTestRoot(entity.getTestRoot());
        existing.setResultsIndexRelativePath(entity.getResultsIndexRelativePath());
        existing.setArtifactRootRelativePath(entity.getArtifactRootRelativePath());
        applyCredentials(existing, entity, existing);
        existing.setEnabled(entity.getEnabled());
        repository.update(existing);
        invalidateDetail(spaceId, id);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long spaceId, Long id) {
        validateSpaceId(spaceId);
        get(spaceId, id);
        repositoryCascadeDeleteService.deleteRepositoryGraph(id);
        invalidateDetail(spaceId, id);
    }

    private Optional<TestRepositoryEntity> getOptional(Long spaceId, Long id) {
        if (detailCacheService == null) {
            return repository.findByIdAndSpaceId(id, spaceId);
        }
        return detailCacheService.getOrLoad(detailCacheKey(spaceId), id, TestRepositoryEntity.class,
                () -> repository.findByIdAndSpaceId(id, spaceId));
    }

    private void invalidateDetail(Long spaceId, Long id) {
        if (detailCacheService != null && id != null) {
            detailCacheService.invalidate(detailCacheKey(spaceId), id);
        }
    }

    private void applyCredentials(TestRepositoryEntity target, TestRepositoryEntity input,
                                  TestRepositoryEntity existing) {
        String requestedAuthType = normalizeAuthType(input.getAuthType());
        String authType = (input.getAuthType() == null || input.getAuthType().isBlank()) && existing != null
                ? normalizeAuthType(existing.getAuthType())
                : requestedAuthType;
        String gitUrl = input.getGitUrl() == null ? "" : input.getGitUrl().trim();
        if (AUTH_HTTPS_TOKEN.equals(authType) && !gitUrl.startsWith("https://")) {
            throw new IllegalArgumentException("HTTPS Token 认证只能用于 https:// 仓库地址");
        }
        if (AUTH_SSH_KEY.equals(authType)
                && !(gitUrl.startsWith("ssh://") || SCP_GIT_URL.matcher(gitUrl).matches())) {
            throw new IllegalArgumentException("SSH Key 认证只能用于 ssh:// 或 git@host:path 仓库地址");
        }

        String previousAuthType = existing == null ? null : normalizeAuthType(existing.getAuthType());
        String previousSecret = existing == null ? null : existing.getCredentialSecret();
        LocalDateTime previousUpdatedAt = existing == null ? null : existing.getCredentialUpdatedAt();
        String rawSecret = input.getCredentialSecret();
        target.setAuthType(authType);
        target.setCredentialUsername(normalizeUsername(input.getCredentialUsername()));

        if (AUTH_PUBLIC.equals(authType)) {
            target.setCredentialSecret(null);
            target.setCredentialUsername(null);
            target.setCredentialUpdatedAt(null);
            return;
        }

        if (rawSecret != null && !rawSecret.isBlank()) {
            target.setCredentialSecret(credentialCipher.encrypt(rawSecret.trim()));
            target.setCredentialUpdatedAt(LocalDateTime.now());
            return;
        }

        boolean existingMatches = existing != null
                && authType.equals(previousAuthType)
                && previousSecret != null
                && !previousSecret.isBlank();
        if (!existingMatches) {
            throw new IllegalArgumentException("私有仓库需要配置 Token 或 SSH 私钥");
        }
        target.setCredentialSecret(previousSecret);
        target.setCredentialUpdatedAt(previousUpdatedAt);
    }

    private String normalizeAuthType(String authType) {
        String normalized = authType == null || authType.isBlank()
                ? AUTH_PUBLIC
                : authType.trim().toUpperCase(Locale.ROOT);
        if (!AUTH_PUBLIC.equals(normalized)
                && !AUTH_HTTPS_TOKEN.equals(normalized)
                && !AUTH_SSH_KEY.equals(normalized)) {
            throw new IllegalArgumentException("不支持的仓库认证方式: " + authType);
        }
        return normalized;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private void validateSpaceId(Long spaceId) {
        if (spaceId == null || spaceId <= 0) {
            throw new IllegalArgumentException("Space not found");
        }
    }

    private String detailCacheKey(Long spaceId) {
        return "repository:%d".formatted(spaceId);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入仓库名称");
        }
        return normalized;
    }

    private void validateUniqueName(String name, Long currentId) {
        boolean duplicated = currentId == null
                ? repository.existsByNameIgnoreCase(name)
                : repository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (duplicated) {
            throw new IllegalStateException("仓库名称已存在，请更换后重试");
        }
    }
}
