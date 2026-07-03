package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SpaceServiceImpl implements SpaceService {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpaceAccessRequestMapper spaceAccessRequestMapper;
    private final SceneMapper sceneMapper;
    private final SceneCascadeDeleteService sceneCascadeDeleteService;
    private final TestRepositoryMapper repositoryMapper;
    private final PlatformUserMapper platformUserMapper;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SpaceServiceImpl(
            SpaceMapper spaceMapper,
            SpaceMemberMapper spaceMemberMapper,
            SpaceAccessRequestMapper spaceAccessRequestMapper,
            SceneMapper sceneMapper,
            SceneCascadeDeleteService sceneCascadeDeleteService,
            TestRepositoryMapper repositoryMapper,
            PlatformUserMapper platformUserMapper,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.spaceAccessRequestMapper = spaceAccessRequestMapper;
        this.sceneMapper = sceneMapper;
        this.sceneCascadeDeleteService = sceneCascadeDeleteService;
        this.repositoryMapper = repositoryMapper;
        this.platformUserMapper = platformUserMapper;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    @Override
    public SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request) {
        SpaceEntity entity = new SpaceEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setOwnerUserId(actor.userId());
        entity.setCreatedBy(actor.userId());
        spaceMapper.insert(entity);

        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(entity.getId());
        member.setUserId(actor.userId());
        member.setRole("ADMIN");
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());
        spaceMemberMapper.insert(member);
        return SpaceSummaryResponse.from(entity);
    }

    @Override
    public List<SpaceSummaryResponse> listMySpaces(AuthContext actor) {
        return spaceMapper.findByUserId(actor.userId()).stream()
                .map(SpaceSummaryResponse::from)
                .toList();
    }

    @Override
    public List<SpacePlazaResponse> listSpacePlaza(AuthContext actor) {
        return spaceMapper.findAll().stream()
                .map(space -> {
                    PlatformUserEntity owner = platformUserMapper.findById(space.getOwnerUserId()).orElse(null);
                    boolean isOwner = actor.userId().equals(space.getOwnerUserId())
                            || actor.userId().equals(space.getCreatedBy());
                    String currentRole = isOwner
                            ? "ADMIN"
                            : spaceMemberMapper.findActiveBySpaceIdAndUserId(space.getId(), actor.userId())
                                    .map(SpaceMemberEntity::getRole)
                                    .orElse(null);
                    String pendingRequestedRole = spaceAccessRequestMapper
                            .findPendingBySpaceIdAndApplicantUserId(space.getId(), actor.userId())
                            .map(SpaceAccessRequestEntity::getRequestedRole)
                            .orElse(null);
                    return SpacePlazaResponse.from(
                            space,
                            owner == null ? space.getOwnerUserId() : owner.getId(),
                            owner == null ? null : owner.getUsername(),
                            resolveNickname(owner == null ? null : owner.getNickname(), owner == null ? null : owner.getUsername()),
                            resolveAvatarUrl(owner == null ? null : owner.getAvatarObjectKey()),
                            currentRole != null,
                            isOwner,
                            currentRole,
                            pendingRequestedRole);
                })
                .toList();
    }

    @Override
    public SpaceSummaryResponse updateSpace(AuthContext actor, Long spaceId, CreateSpaceRequest request) {
        SpaceEntity entity = requireOwnedSpace(actor, spaceId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        spaceMapper.update(entity);
        return SpaceSummaryResponse.from(entity);
    }

    @Override
    @Transactional
    public void deleteSpace(AuthContext actor, Long spaceId) {
        requireOwnedSpace(actor, spaceId);
        sceneMapper.findAllBySpaceId(spaceId).forEach(scene -> sceneCascadeDeleteService.deleteSceneGraph(scene.getId()));
        repositoryMapper.deleteAllBySpaceId(spaceId);
        spaceAccessRequestMapper.deleteBySpaceId(spaceId);
        spaceMemberMapper.deleteBySpaceId(spaceId);
        spaceMapper.deleteById(spaceId);
    }

    private SpaceEntity requireOwnedSpace(AuthContext actor, Long spaceId) {
        SpaceEntity entity = spaceMapper.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found: " + spaceId));
        if (!actor.userId().equals(entity.getOwnerUserId()) && !actor.userId().equals(entity.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space admin required");
        }
        return entity;
    }

    private String resolveAvatarUrl(String avatarObjectKey) {
        if (avatarObjectKey == null || avatarObjectKey.isBlank()) {
            return null;
        }
        try {
            return objectStorageService.createPresignedGetUrl(storageBucket, avatarObjectKey);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private String resolveNickname(String nickname, String username) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        return username == null || username.isBlank() ? "未命名用户" : username.trim();
    }
}
