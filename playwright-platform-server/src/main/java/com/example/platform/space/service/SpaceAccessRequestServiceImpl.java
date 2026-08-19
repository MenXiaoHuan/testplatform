package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.common.BusinessException;
import com.example.platform.space.dto.SpaceAccessRequestProjection;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 空间访问申请服务实现类，提供访问申请的提交、查询和审批功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提交访问申请（校验空间存在性、角色重复性、待处理申请冲突）</li>
 *   <li>列出指定空间的访问申请（含申请人信息）</li>
 *   <li>批准访问申请（自动更新或创建成员关系）</li>
 *   <li>拒绝访问申请</li>
 *   <li>将申请投影转换为响应对象</li>
 *   <li>生成申请人头像的预签名 URL</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceMapper} - 空间数据访问接口</li>
 *   <li>{@link SpaceMemberMapper} - 空间成员数据访问接口</li>
 *   <li>{@link SpaceAccessRequestMapper} - 访问申请数据访问接口</li>
 *   <li>{@link ObjectStorageService} - 对象存储服务</li>
 * </ul>
 */
@Service
public class SpaceAccessRequestServiceImpl implements SpaceAccessRequestService {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpaceAccessRequestMapper spaceAccessRequestMapper;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;

    public SpaceAccessRequestServiceImpl(
            SpaceMapper spaceMapper,
            SpaceMemberMapper spaceMemberMapper,
            SpaceAccessRequestMapper spaceAccessRequestMapper,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.spaceAccessRequestMapper = spaceAccessRequestMapper;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
    }

    /**
     * 提交访问空间的申请
     * 会进行以下校验：空间存在性、角色是否重复、是否有待处理申请
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 目标空间ID
     * @param request 提交申请请求体
     * @throws IllegalArgumentException 如果空间不存在
     * @throws IllegalStateException 如果已拥有该角色或有待处理申请
     */
    @Override
    public void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request) {
        // 校验空间存在性
        spaceMapper.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("space not found"));

        // 校验用户是否已拥有相同角色
        SpaceMemberEntity currentMember = spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, actor.userId()).orElse(null);
        if (currentMember != null && currentMember.getRole().equalsIgnoreCase(request.requestedRole())) {
            throw new IllegalStateException("already has requested role");
        }
        // 校验是否已有待处理的申请
        if (spaceAccessRequestMapper.findPendingBySpaceIdAndApplicantUserId(spaceId, actor.userId()).isPresent()) {
            throw new IllegalStateException("request already pending");
        }

        // 创建访问申请记录
        SpaceAccessRequestEntity entity = new SpaceAccessRequestEntity();
        entity.setSpaceId(spaceId);
        entity.setApplicantUserId(actor.userId());
        entity.setRequestedRole(request.requestedRole());
        entity.setReason(request.reason());
        entity.setStatus("PENDING");
        spaceAccessRequestMapper.insert(entity);
    }

    /**
     * 列出指定空间的所有访问申请
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     * @return 访问申请响应列表
     */
    @Override
    public List<SpaceAccessRequestResponse> listBySpace(Long spaceId, AuthContext actor) {
        try {
            return spaceAccessRequestMapper.findProjectionBySpaceId(spaceId).stream()
                    .map(this::toResponse)
                    .toList();
        } catch (RuntimeException exception) {
            throw new BusinessException("ACCESS_REQUEST_LIST_FAILED", "审批列表加载失败，请刷新后重试");
        }
    }

    /**
     * 批准访问申请
     * 如果申请人已是成员，则更新其角色；否则创建新的成员关系
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体
     */
    @Override
    public void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        // 查找申请记录并校验空间归属
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));

        // 更新申请状态为已批准
        spaceAccessRequestMapper.updateReview(requestId, "APPROVED", request.reviewComment(), actor.userId());

        // 处理成员关系
        SpaceMemberEntity existingMember = spaceMemberMapper
                .findActiveBySpaceIdAndUserId(spaceId, entity.getApplicantUserId())
                .orElse(null);
        if (existingMember == null) {
            // 申请人不是成员，创建新的成员关系
            SpaceMemberEntity member = new SpaceMemberEntity();
            member.setSpaceId(spaceId);
            member.setUserId(entity.getApplicantUserId());
            member.setRole(entity.getRequestedRole());
            member.setStatus("ACTIVE");
            member.setJoinedAt(LocalDateTime.now());
            spaceMemberMapper.insert(member);
            return;
        }
        // 申请人已是成员，更新其角色
        spaceMemberMapper.updateRole(spaceId, entity.getApplicantUserId(), entity.getRequestedRole());
    }

    /**
     * 拒绝访问申请
     *
     * @param actor 当前操作用户的认证上下文
     * @param spaceId 空间ID
     * @param requestId 申请ID
     * @param request 审批请求体
     */
    @Override
    public void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        // 查找申请记录并校验空间归属
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));
        // 更新申请状态为已拒绝
        spaceAccessRequestMapper.updateReview(entity.getId(), "REJECTED", request.reviewComment(), actor.userId());
    }

    /**
     * 将访问申请投影转换为响应对象
     *
     * @param item 访问申请投影
     * @return 访问申请响应
     */
    private SpaceAccessRequestResponse toResponse(SpaceAccessRequestProjection item) {
        // 解析昵称，优先使用昵称，其次使用用户名
        String nickname = item.getApplicantNickname() == null || item.getApplicantNickname().isBlank()
                ? item.getApplicantUsername()
                : item.getApplicantNickname().trim();
        return new SpaceAccessRequestResponse(
                item.getId(),
                item.getSpaceId(),
                item.getApplicantUserId(),
                item.getApplicantUsername(),
                nickname,
                resolveAvatarUrl(item.getApplicantAvatarObjectKey()),
                item.getRequestedRole(),
                item.getReason(),
                item.getStatus(),
                item.getReviewComment(),
                item.getReviewedBy(),
                item.getReviewedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt());
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
}