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

    @Override
    public void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request) {
        spaceMapper.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("space not found"));

        SpaceMemberEntity currentMember = spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, actor.userId()).orElse(null);
        if (currentMember != null && currentMember.getRole().equalsIgnoreCase(request.requestedRole())) {
            throw new IllegalStateException("already has requested role");
        }
        if (spaceAccessRequestMapper.findPendingBySpaceIdAndApplicantUserId(spaceId, actor.userId()).isPresent()) {
            throw new IllegalStateException("request already pending");
        }

        SpaceAccessRequestEntity entity = new SpaceAccessRequestEntity();
        entity.setSpaceId(spaceId);
        entity.setApplicantUserId(actor.userId());
        entity.setRequestedRole(request.requestedRole());
        entity.setReason(request.reason());
        entity.setStatus("PENDING");
        spaceAccessRequestMapper.insert(entity);
    }

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

    @Override
    public void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));

        spaceAccessRequestMapper.updateReview(requestId, "APPROVED", request.reviewComment(), actor.userId());

        SpaceMemberEntity existingMember = spaceMemberMapper
                .findActiveBySpaceIdAndUserId(spaceId, entity.getApplicantUserId())
                .orElse(null);
        if (existingMember == null) {
            SpaceMemberEntity member = new SpaceMemberEntity();
            member.setSpaceId(spaceId);
            member.setUserId(entity.getApplicantUserId());
            member.setRole(entity.getRequestedRole());
            member.setStatus("ACTIVE");
            member.setJoinedAt(LocalDateTime.now());
            spaceMemberMapper.insert(member);
            return;
        }
        spaceMemberMapper.updateRole(spaceId, entity.getApplicantUserId(), entity.getRequestedRole());
    }

    @Override
    public void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));
        spaceAccessRequestMapper.updateReview(entity.getId(), "REJECTED", request.reviewComment(), actor.userId());
    }

    private SpaceAccessRequestResponse toResponse(SpaceAccessRequestProjection item) {
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
