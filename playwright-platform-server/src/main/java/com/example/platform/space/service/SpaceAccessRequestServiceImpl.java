package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SpaceAccessRequestServiceImpl implements SpaceAccessRequestService {
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpaceAccessRequestMapper spaceAccessRequestMapper;

    public SpaceAccessRequestServiceImpl(
            SpaceMemberMapper spaceMemberMapper,
            SpaceAccessRequestMapper spaceAccessRequestMapper) {
        this.spaceMemberMapper = spaceMemberMapper;
        this.spaceAccessRequestMapper = spaceAccessRequestMapper;
    }

    @Override
    public void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request) {
        if (spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, actor.userId()).isPresent()) {
            throw new IllegalStateException("already a member");
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
    public List<SpaceAccessRequestEntity> listBySpace(Long spaceId, AuthContext actor) {
        return spaceAccessRequestMapper.findBySpaceId(spaceId);
    }

    @Override
    public void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));

        spaceAccessRequestMapper.updateReview(requestId, "APPROVED", request.reviewComment(), actor.userId());

        if (spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, entity.getApplicantUserId()).isEmpty()) {
            SpaceMemberEntity member = new SpaceMemberEntity();
            member.setSpaceId(spaceId);
            member.setUserId(entity.getApplicantUserId());
            member.setRole(entity.getRequestedRole());
            member.setStatus("ACTIVE");
            member.setJoinedAt(LocalDateTime.now());
            spaceMemberMapper.insert(member);
        }
    }

    @Override
    public void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request) {
        SpaceAccessRequestEntity entity = spaceAccessRequestMapper.findById(requestId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new IllegalArgumentException("space access request not found"));
        spaceAccessRequestMapper.updateReview(entity.getId(), "REJECTED", request.reviewComment(), actor.userId());
    }
}
