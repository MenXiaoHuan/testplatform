package com.example.platform.space;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.mapper.PlatformUserMapper;
import com.example.platform.auth.model.PlatformUserEntity;
import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.space.dto.SpaceAccessRequestProjection;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneCascadeDeleteService;
import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.mapper.SpaceAccessRequestMapper;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import com.example.platform.space.service.SpaceServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceServiceTest {
    @Test
    void shouldAddCreatorAsAdminWhenSpaceIsCreated() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceServiceImpl service = new SpaceServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                mockSceneMapper(),
                mockSceneCascadeDeleteService(),
                mockRepositoryMapper(),
                mockPlatformUserMapper(),
                mockObjectStorageService(),
                "qa-report");

        SpaceSummaryResponse response = service.createSpace(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                new CreateSpaceRequest("默认空间", "desc"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("默认空间");
        assertThat(memberMapper.findActiveBySpaceIdAndUserId(1L, 1L))
                .map(SpaceMemberEntity::getRole)
                .contains("ADMIN");
    }

    @Test
    void shouldUpdateAndDeleteOwnedSpace() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceServiceImpl service = new SpaceServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                mockSceneMapper(),
                mockSceneCascadeDeleteService(),
                mockRepositoryMapper(),
                mockPlatformUserMapper(),
                mockObjectStorageService(),
                "qa-report");

        service.createSpace(new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null), new CreateSpaceRequest("默认空间", "desc"));

        SpaceSummaryResponse updated = service.updateSpace(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                1L,
                new CreateSpaceRequest("新的空间", "新的说明"));

        assertThat(updated.name()).isEqualTo("新的空间");
        assertThat(spaceMapper.findById(1L)).map(SpaceEntity::getDescription).contains("新的说明");

        service.deleteSpace(new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null), 1L);
        assertThat(spaceMapper.findById(1L)).isEmpty();
    }

    @Test
    void shouldDeleteSpaceChildrenBeforeRemovingSpace() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SceneMapper sceneMapper = mockSceneMapper();
        SceneCascadeDeleteService sceneCascadeDeleteService = mockSceneCascadeDeleteService();
        TestRepositoryMapper repositoryMapper = mockRepositoryMapper();
        SpaceServiceImpl service = new SpaceServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                sceneMapper,
                sceneCascadeDeleteService,
                repositoryMapper,
                mockPlatformUserMapper(),
                mockObjectStorageService(),
                "qa-report");

        SpaceSummaryResponse created = service.createSpace(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                new CreateSpaceRequest("默认空间", "desc"));

        com.example.platform.space.model.SpaceAccessRequestEntity pending = new com.example.platform.space.model.SpaceAccessRequestEntity();
        pending.setSpaceId(created.id());
        pending.setApplicantUserId(2L);
        pending.setRequestedRole("VIEWER");
        pending.setReason("申请进入");
        pending.setStatus("PENDING");
        requestMapper.insert(pending);

        SceneEntity scene = new SceneEntity();
        scene.setId(9L);
        scene.setSpaceId(created.id());
        scene.setRepoId(7L);
        Mockito.when(sceneMapper.findAllBySpaceId(created.id())).thenReturn(List.of(scene));

        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setId(7L);
        repository.setSpaceId(created.id());
        Mockito.when(repositoryMapper.findAllBySpaceId(created.id())).thenReturn(List.of(repository));

        service.deleteSpace(new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null), created.id());

        assertThat(spaceMapper.findById(created.id())).isEmpty();
        assertThat(memberMapper.findBySpaceId(created.id())).isEmpty();
        assertThat(requestMapper.findBySpaceId(created.id())).isEmpty();
        Mockito.verify(sceneCascadeDeleteService).deleteSceneGraph(9L);
        Mockito.verify(repositoryMapper).deleteAllBySpaceId(created.id());
    }

    @Test
    void shouldListSpacePlazaWithAccessRoleAndPendingRequest() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        InMemorySpaceAccessRequestMapper requestMapper = new InMemorySpaceAccessRequestMapper();
        SpaceServiceImpl service = new SpaceServiceImpl(
                spaceMapper,
                memberMapper,
                requestMapper,
                mockSceneMapper(),
                mockSceneCascadeDeleteService(),
                mockRepositoryMapper(),
                mockPlatformUserMapper(),
                mockObjectStorageService(),
                "qa-report");

        SpaceEntity defaultSpace = new SpaceEntity();
        defaultSpace.setName("默认空间");
        defaultSpace.setDescription("desc");
        defaultSpace.setOwnerUserId(1L);
        defaultSpace.setCreatedBy(1L);
        spaceMapper.insert(defaultSpace);

        SpaceEntity otherSpace = new SpaceEntity();
        otherSpace.setName("其他空间");
        otherSpace.setDescription("other");
        otherSpace.setOwnerUserId(9L);
        otherSpace.setCreatedBy(9L);
        spaceMapper.insert(otherSpace);

        var pending = new com.example.platform.space.model.SpaceAccessRequestEntity();
        pending.setSpaceId(otherSpace.getId());
        pending.setApplicantUserId(1L);
        pending.setRequestedRole("OPERATOR");
        pending.setReason("需要处理任务");
        pending.setStatus("PENDING");
        requestMapper.insert(pending);

        List<SpacePlazaResponse> result = service.listSpacePlaza(new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).accessible()).isTrue();
        assertThat(result.get(0).manageable()).isTrue();
        assertThat(result.get(0).currentRole()).isEqualTo("ADMIN");
        assertThat(result.get(0).ownerUsername()).isEqualTo("owner-1");
        assertThat(result.get(1).accessible()).isFalse();
        assertThat(result.get(1).manageable()).isFalse();
        assertThat(result.get(1).pendingRequestedRole()).isEqualTo("OPERATOR");
    }

    private static SceneMapper mockSceneMapper() {
        SceneMapper mapper = Mockito.mock(SceneMapper.class);
        Mockito.when(mapper.findAllBySpaceId(Mockito.anyLong())).thenReturn(List.of());
        return mapper;
    }

    private static SceneCascadeDeleteService mockSceneCascadeDeleteService() {
        return Mockito.mock(SceneCascadeDeleteService.class);
    }

    private static TestRepositoryMapper mockRepositoryMapper() {
        TestRepositoryMapper mapper = Mockito.mock(TestRepositoryMapper.class);
        Mockito.when(mapper.findAllBySpaceId(Mockito.anyLong())).thenReturn(List.of());
        return mapper;
    }

    private static PlatformUserMapper mockPlatformUserMapper() {
        PlatformUserMapper mapper = Mockito.mock(PlatformUserMapper.class);
        Mockito.when(mapper.findById(Mockito.anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            PlatformUserEntity user = new PlatformUserEntity();
            user.setId(id);
            user.setUsername("owner-" + id);
            user.setNickname("Owner " + id);
            user.setAvatarObjectKey("avatars/owner-" + id + ".png");
            return Optional.of(user);
        });
        return mapper;
    }

    private static ObjectStorageService mockObjectStorageService() {
        ObjectStorageService storage = Mockito.mock(ObjectStorageService.class);
        Mockito.when(storage.createPresignedGetUrl(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> "http://localhost:10000/%s/%s".formatted(invocation.getArgument(0), invocation.getArgument(1)));
        return storage;
    }

    private static final class InMemorySpaceMapper implements SpaceMapper {
        private long nextId = 1L;
        private final List<SpaceEntity> spaces = new ArrayList<>();

        @Override
        public int insert(SpaceEntity entity) {
            entity.setId(nextId++);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
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
            entity.setUpdatedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int deleteById(Long id) {
            return spaces.removeIf(item -> item.getId().equals(id)) ? 1 : 0;
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
            return 0;
        }

        @Override
        public int deleteBySpaceId(Long spaceId) {
            int before = members.size();
            members.removeIf(item -> item.getSpaceId().equals(spaceId));
            return before - members.size();
        }
    }

    private static final class InMemorySpaceAccessRequestMapper implements SpaceAccessRequestMapper {
        private final List<com.example.platform.space.model.SpaceAccessRequestEntity> requests = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public int insert(com.example.platform.space.model.SpaceAccessRequestEntity entity) {
            entity.setId(nextId++);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            requests.add(entity);
            return 1;
        }

        @Override
        public Optional<com.example.platform.space.model.SpaceAccessRequestEntity> findById(Long id) {
            return requests.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<com.example.platform.space.model.SpaceAccessRequestEntity> findPendingBySpaceIdAndApplicantUserId(Long spaceId, Long applicantUserId) {
            return requests.stream()
                    .filter(item -> item.getSpaceId().equals(spaceId)
                            && item.getApplicantUserId().equals(applicantUserId)
                            && "PENDING".equals(item.getStatus()))
                    .findFirst();
        }

        @Override
        public List<com.example.platform.space.model.SpaceAccessRequestEntity> findBySpaceId(Long spaceId) {
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
            return 0;
        }

        @Override
        public int deleteBySpaceId(Long spaceId) {
            int before = requests.size();
            requests.removeIf(item -> item.getSpaceId().equals(spaceId));
            return before - requests.size();
        }
    }
}
