package com.example.platform.space;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.SpaceAccessRequestProjection;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import com.example.platform.space.service.SpaceAccessRequestServiceImpl;
import com.example.platform.storage.service.ObjectStorageService;
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
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        spaceMapper.insert(space(7L));
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        requestMapper.insert(pendingRequest(7L, 2L));
        SpaceAccessRequestServiceImpl service = new SpaceAccessRequestServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                new NoopObjectStorageService(),
                "qa-report");

        assertThatThrownBy(() -> service.submitRequest(
                new AuthContext(2L, "alice", "徐个愿", "avatars/alice.png", null),
                7L,
                new SubmitSpaceAccessRequestRequest("OPERATOR", "需要查看并处理任务")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request already pending");
    }

    @Test
    void shouldApproveRequestAndCreateActiveMember() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        spaceMapper.insert(space(7L));
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceAccessRequestEntity entity = pendingRequest(7L, 2L);
        requestMapper.insert(entity);
        SpaceAccessRequestServiceImpl service = new SpaceAccessRequestServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                new NoopObjectStorageService(),
                "qa-report");

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

    @Test
    void shouldAllowViewerToRequestHigherRoleAndUpgradeOnApproval() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        spaceMapper.insert(space(7L));
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceMemberEntity viewer = new SpaceMemberEntity();
        viewer.setSpaceId(7L);
        viewer.setUserId(2L);
        viewer.setRole("VIEWER");
        viewer.setStatus("ACTIVE");
        viewer.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(viewer);
        SpaceAccessRequestServiceImpl service = new SpaceAccessRequestServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                new NoopObjectStorageService(),
                "qa-report");

        service.submitRequest(
                new AuthContext(2L, "alice", "徐个愿", "avatars/alice.png", null),
                7L,
                new SubmitSpaceAccessRequestRequest("OPERATOR", "需要处理任务"));

        SpaceAccessRequestEntity request = requestMapper.findPendingBySpaceIdAndApplicantUserId(7L, 2L).orElseThrow();
        service.approveRequest(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                7L,
                request.getId(),
                new ReviewSpaceAccessRequestRequest("升级权限"));

        assertThat(memberMapper.findActiveBySpaceIdAndUserId(7L, 2L))
                .map(SpaceMemberEntity::getRole)
                .contains("OPERATOR");
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

    private static SpaceEntity space(Long id) {
        SpaceEntity entity = new SpaceEntity();
        entity.setId(id);
        entity.setName("空间-" + id);
        entity.setDescription("desc");
        entity.setOwnerUserId(1L);
        entity.setCreatedBy(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static final class InMemorySpaceMapper implements SpaceMapper {
        private final List<SpaceEntity> spaces = new ArrayList<>();

        @Override
        public int insert(SpaceEntity entity) {
            spaces.removeIf(item -> item.getId().equals(entity.getId()));
            spaces.add(entity);
            return 1;
        }

        @Override
        public Optional<SpaceEntity> findByName(String name) {
            return spaces.stream().filter(item -> item.getName().equals(name)).findFirst();
        }

        @Override
        public Optional<SpaceEntity> findById(Long id) {
            return spaces.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<SpaceEntity> findAll() {
            return List.copyOf(spaces);
        }

        @Override
        public List<SpaceEntity> findByUserId(Long userId) {
            return List.of();
        }

        @Override
        public int update(SpaceEntity entity) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }
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

        @Override
        public int updateRole(Long spaceId, Long userId, String role) {
            Optional<SpaceMemberEntity> member = findActiveBySpaceIdAndUserId(spaceId, userId);
            if (member.isEmpty()) {
                return 0;
            }
            member.get().setRole(role);
            return 1;
        }

        @Override
        public int deleteBySpaceId(Long spaceId) {
            int before = members.size();
            members.removeIf(item -> item.getSpaceId().equals(spaceId));
            return before - members.size();
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
        public List<SpaceAccessRequestProjection> findProjectionBySpaceId(Long spaceId) {
            return requests.stream()
                    .filter(item -> item.getSpaceId().equals(spaceId))
                    .map(item -> new SpaceAccessRequestProjection(
                            item.getId(),
                            item.getSpaceId(),
                            item.getApplicantUserId(),
                            "user-" + item.getApplicantUserId(),
                            "昵称-" + item.getApplicantUserId(),
                            "avatars/user-" + item.getApplicantUserId() + ".png",
                            item.getRequestedRole(),
                            item.getReason(),
                            item.getStatus(),
                            item.getReviewComment(),
                            item.getReviewedBy(),
                            item.getReviewedAt(),
                            item.getCreatedAt(),
                            item.getUpdatedAt()))
                    .toList();
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

        @Override
        public int deleteBySpaceId(Long spaceId) {
            int before = requests.size();
            requests.removeIf(item -> item.getSpaceId().equals(spaceId));
            return before - requests.size();
        }
    }

    private static final class NoopObjectStorageService implements ObjectStorageService {
        @Override
        public String uploadDirectory(String bucket, String objectPrefix, java.nio.file.Path sourceDirectory) {
            return "";
        }

        @Override
        public String uploadFile(String bucket, String objectKey, java.nio.file.Path sourceFile) {
            return "";
        }

        @Override
        public String createPresignedGetUrl(String bucket, String objectKey) {
            return "http://localhost:10000/%s/%s".formatted(bucket, objectKey);
        }

        @Override
        public java.io.InputStream getObject(String bucket, String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteObject(String bucket, String objectKey) {
        }
    }
}
