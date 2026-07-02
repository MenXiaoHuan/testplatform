package com.example.platform.space;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.CreateSpaceRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

class SpaceServiceTest {
    @Test
    void shouldAddCreatorAsAdminWhenSpaceIsCreated() {
        InMemorySpaceMapper spaceMapper = new InMemorySpaceMapper();
        InMemorySpaceMemberMapper memberMapper = new InMemorySpaceMemberMapper();
        SpaceServiceImpl service = new SpaceServiceImpl(spaceMapper, memberMapper);

        SpaceSummaryResponse response = service.createSpace(
                new AuthContext(1L, "admin", "平台管理员", "avatars/admin.png", null),
                new CreateSpaceRequest("默认空间", "desc"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("默认空间");
        assertThat(memberMapper.findActiveBySpaceIdAndUserId(1L, 1L))
                .map(SpaceMemberEntity::getRole)
                .contains("ADMIN");
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
        public Optional<SpaceEntity> findById(Long id) {
            return spaces.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<SpaceEntity> findByUserId(Long userId) {
            return List.of();
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
    }
}
