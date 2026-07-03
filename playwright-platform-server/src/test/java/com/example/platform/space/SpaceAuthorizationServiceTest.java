package com.example.platform.space;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import com.example.platform.space.service.SpaceAuthorizationServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceAuthorizationServiceTest {
    @Test
    void shouldAllowViewerToReadButNotOperate() {
        SpaceMapper spaceMapper = Mockito.mock(SpaceMapper.class);
        SpaceMemberMapper memberMapper = Mockito.mock(SpaceMemberMapper.class);
        SpaceAuthorizationServiceImpl service = new SpaceAuthorizationServiceImpl(spaceMapper, memberMapper);
        AuthContext actor = new AuthContext(1L, "alice", "Alice", null, 7L);

        SpaceEntity space = new SpaceEntity();
        space.setId(7L);
        SpaceMemberEntity viewer = new SpaceMemberEntity();
        viewer.setSpaceId(7L);
        viewer.setUserId(1L);
        viewer.setRole("VIEWER");
        viewer.setStatus("ACTIVE");

        Mockito.when(spaceMapper.findById(7L)).thenReturn(Optional.of(space));
        Mockito.when(memberMapper.findActiveBySpaceIdAndUserId(7L, 1L)).thenReturn(Optional.of(viewer));

        assertThatCode(() -> service.requireReadableSpace(7L, actor)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireOperableSpace(7L, actor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void shouldRejectUnknownSpaceOrNonMember() {
        SpaceMapper spaceMapper = Mockito.mock(SpaceMapper.class);
        SpaceMemberMapper memberMapper = Mockito.mock(SpaceMemberMapper.class);
        SpaceAuthorizationServiceImpl service = new SpaceAuthorizationServiceImpl(spaceMapper, memberMapper);
        AuthContext actor = new AuthContext(1L, "alice", "Alice", null, null);

        Mockito.when(spaceMapper.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireReadableSpace(99L, actor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        SpaceEntity space = new SpaceEntity();
        space.setId(7L);
        Mockito.when(spaceMapper.findById(7L)).thenReturn(Optional.of(space));
        Mockito.when(memberMapper.findActiveBySpaceIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireReadableSpace(7L, actor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }
}
