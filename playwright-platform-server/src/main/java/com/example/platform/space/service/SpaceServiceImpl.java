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

/**
 * 空间业务逻辑实现类，提供空间的创建、更新、删除和查询功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建空间并自动将创建者添加为管理员成员</li>
 *   <li>更新空间信息（仅所有者可操作）</li>
 *   <li>删除空间及其所有关联数据（场景、仓库、成员、访问申请）</li>
 *   <li>列出当前用户参与的所有空间</li>
 *   <li>获取空间广场列表，包含访问状态和待审批申请</li>
 *   <li>生成用户头像的预签名 URL</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceMapper} - 空间数据访问接口</li>
 *   <li>{@link SpaceMemberMapper} - 空间成员数据访问接口</li>
 *   <li>{@link SpaceAccessRequestMapper} - 访问申请数据访问接口</li>
 *   <li>{@link SceneMapper} - 场景数据访问接口</li>
 *   <li>{@link SceneCascadeDeleteService} - 场景级联删除服务</li>
 *   <li>{@link TestRepositoryMapper} - 测试仓库数据访问接口</li>
 *   <li>{@link PlatformUserMapper} - 用户数据访问接口</li>
 *   <li>{@link ObjectStorageService} - 对象存储服务</li>
 * </ul>
 */
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

    /**
     * 创建新空间
     * 创建者自动成为空间管理员
     *
     * @param actor 当前操作用户的认证上下文
     * @param request 创建空间请求体
     * @return 创建的空间摘要
     */
    @Override
    public SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request) {
        SpaceEntity entity = new SpaceEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setOwnerUserId(actor.userId());
        entity.setCreatedBy(actor.userId());
        spaceMapper.insert(entity);

        // 将创建者添加为空间管理员
        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(entity.getId());
        member.setUserId(actor.userId());
        member.setRole("ADMIN");
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());
        spaceMemberMapper.insert(member);
        return SpaceSummaryResponse.from(entity);
    }

    /**
     * 列出当前用户参与的所有空间
     *
     * @param actor 当前操作用户的认证上下文
     * @return 空间摘要列表
     */
    @Override
    public List<SpaceSummaryResponse> listMySpaces(AuthContext actor) {
        return spaceMapper.findByUserId(actor.userId()).stream()
                .map(SpaceSummaryResponse::from)
                .toList();
    }

    /**
     * 获取空间广场列表
     * 包含所有空间信息及当前用户的访问状态
     *
     * @param actor 当前操作用户的认证上下文
     * @return 空间广场响应列表
     */
    @Override
    public List<SpacePlazaResponse> listSpacePlaza(AuthContext actor) {
        return spaceMapper.findAll().stream()
                .map(space -> {
                    // 查询空间所有者信息
                    PlatformUserEntity owner = platformUserMapper.findById(space.getOwnerUserId()).orElse(null);
                    // 判断当前用户是否为空间所有者或创建者
                    boolean isOwner = actor.userId().equals(space.getOwnerUserId())
                            || actor.userId().equals(space.getCreatedBy());
                    // 获取当前用户在空间中的角色
                    String currentRole = isOwner
                            ? "ADMIN"
                            : spaceMemberMapper.findActiveBySpaceIdAndUserId(space.getId(), actor.userId())
                                    .map(SpaceMemberEntity::getRole)
                                    .orElse(null);
                    // 获取当前用户在该空间的待审批申请角色
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

    /**
     * 更新指定空间信息
     * 仅空间所有者或创建者可操作
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param request 更新请求体
     * @return 更新后的空间摘要
     */
    @Override
    public SpaceSummaryResponse updateSpace(AuthContext actor, Long spaceId, CreateSpaceRequest request) {
        SpaceEntity entity = requireOwnedSpace(actor, spaceId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        spaceMapper.update(entity);
        return SpaceSummaryResponse.from(entity);
    }

    /**
     * 删除指定空间及其所有关联数据
     * 级联删除：场景 -> 测试仓库 -> 访问申请 -> 成员 -> 空间
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     */
    @Override
    @Transactional
    public void deleteSpace(AuthContext actor, Long spaceId) {
        requireOwnedSpace(actor, spaceId);
        // 级联删除场景
        sceneMapper.findAllBySpaceId(spaceId).forEach(scene -> sceneCascadeDeleteService.deleteSceneGraph(scene.getId()));
        // 删除测试仓库
        repositoryMapper.deleteAllBySpaceId(spaceId);
        // 删除访问申请
        spaceAccessRequestMapper.deleteBySpaceId(spaceId);
        // 删除空间成员
        spaceMemberMapper.deleteBySpaceId(spaceId);
        // 最后删除空间本身
        spaceMapper.deleteById(spaceId);
    }

    /**
     * 校验用户是否为空间所有者或创建者
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @return 空间实体
     * @throws ResponseStatusException 如果空间不存在或用户无权限
     */
    private SpaceEntity requireOwnedSpace(AuthContext actor, Long spaceId) {
        SpaceEntity entity = spaceMapper.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found: " + spaceId));
        if (!actor.userId().equals(entity.getOwnerUserId()) && !actor.userId().equals(entity.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space admin required");
        }
        return entity;
    }

    /**
     * 根据对象存储键生成头像访问 URL
     *
     * @param avatarObjectKey 头像对象存储键
     * @return 头像访问 URL，如果键为空则返回 null
     */
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

    /**
     * 解析用户昵称，优先使用昵称，其次使用用户名，最后使用默认值
     *
     * @param nickname 用户昵称
     * @param username 用户名
     * @return 解析后的昵称
     */
    private String resolveNickname(String nickname, String username) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        return username == null || username.isBlank() ? "未命名用户" : username.trim();
    }
}