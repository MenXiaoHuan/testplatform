package com.example.platform.space;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import com.example.platform.space.service.SpaceAccessRequestServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceAccessRequestServiceTest {
    @Test
    void shouldRejectDuplicatePendingRequestForSameSpace() {
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        requestMapper.insert(pendingRequest(7L, 2L));
        SpaceAccessRequestServiceImpl service = new SpaceAccessRequestServiceImpl(memberMapper, requestMapper);

        assertThatThrownBy(() -> service.submitRequest(
                new AuthContext(2L, "alice", "徐个愿", "avatars/alice.png", null),
                7L,
                new SubmitSpaceAccessRequestRequest("OPERATOR", "需要查看并处理任务")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request already pending");
    }

    @Test
    void shouldApproveRequestAndCreateActiveMember() {
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceAccessRequestEntity entity = pendingRequest(7L, 2L);
        requestMapper.insert(entity);
        SpaceAccessRequestServiceImpl service = new SpaceAccessRequestServiceImpl(memberMapper, requestMapper);

        service.approveRequest(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                7L,
                entity.getId(),
                new ReviewSpaceAccessRequestRequest("同意加入"));

        assertThat(memberMapper.findActiveBySpaceIdAndUserId(7L, 2L))
                .map(SpaceMemberEntity::getRole)
                .contains("OPERATOR");
        assertThat(requestMapper.findById(entity.getId()))
                .map(SpaceAccessRequestEntity::getStatus)
                .contains("APPROVED");
    }

    private static SpaceAccessRequestEntity pendingRequest(Long spaceId, Long applicantUserId) {
        SpaceAccessRequestEntity entity = new SpaceAccessRequestEntity();
        entity.setSpaceId(spaceId);
        entity.setApplicantUserId(applicantUserId);
        entity.setRequestedRole("OPERATOR");
        entity.setReason("需要处理任务");
        entity.setStatus("PENDING");
        return entity;
    }

    private static final class InMemorySpaceMemberMapper implements SpaceMemberMapper {
        private long nextId = 1L;
        private final List<SpaceMemberEntity> members = new ArrayList<>();

        @Override
        public int insert(SpaceMemberEntity entity) {
            entity.setId(nextId++);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            members.add(entity);
            return 1;
        }

        @Override
        public Optional<SpaceMemberEntity> findActiveBySpaceIdAndUserId(Long spaceId, Long userId) {
            return members.stream()
                    .filter(item -> item.getSpaceId().equals(spaceId) && item.getUserId().equals(userId) && "ACTIVE".equals(item.getStatus()))
                    .findFirst();
        }

        @Override
        public List<SpaceMemberEntity> findBySpaceId(Long spaceId) {
            return members.stream().filter(item -> item.getSpaceId().equals(spaceId)).toList();
        }

        @Override
        public int updateStatus(Long spaceId, Long userId, String status) {
            return 0;
        }
    }

    private static final class InMemorySpaceAccessRequestMapper implements SpaceAccessRequestMapper {
        private long nextId = 1L;
        private final List<SpaceAccessRequestEntity> requests = new ArrayList<>();

        @Override
        public int insert(SpaceAccessRequestEntity entity) {
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            requests.add(entity);
            return 1;
        }

        @Override
        public Optional<SpaceAccessRequestEntity> findById(Long id) {
            return requests.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<SpaceAccessRequestEntity> findPendingBySpaceIdAndApplicantUserId(Long spaceId, Long applicantUserId) {
            return requests.stream()
                    .filter(item -> item.getSpaceId().equals(spaceId)
                            && item.getApplicantUserId().equals(applicantUserId)
                            && "PENDING".equals(item.getStatus()))
                    .findFirst();
        }

        @Override
        public List<SpaceAccessRequestEntity> findBySpaceId(Long spaceId) {
            return requests.stream().filter(item -> item.getSpaceId().equals(spaceId)).toList();
        }

        @Override
        public int updateReview(Long requestId, String status, String reviewComment, Long reviewedBy) {
            Optional<SpaceAccessRequestEntity> request = findById(requestId);
            if (request.isEmpty()) {
                return 0;
            }
            request.get().setStatus(status);
            request.get().setReviewComment(reviewComment);
            request.get().setReviewedBy(reviewedBy);
            request.get().setReviewedAt(LocalDateTime.now());
            return 1;
        }
    }
}
