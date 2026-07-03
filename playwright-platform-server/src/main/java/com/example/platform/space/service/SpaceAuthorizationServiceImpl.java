package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceMemberEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SpaceAuthorizationServiceImpl implements SpaceAuthorizationService {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;

    public SpaceAuthorizationServiceImpl(SpaceMapper spaceMapper, SpaceMemberMapper spaceMemberMapper) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
    }

    @Override
    public void requireReadableSpace(Long spaceId, AuthContext actor) {
        requireMember(spaceId, actor);
    }

    @Override
    public void requireOperableSpace(Long spaceId, AuthContext actor) {
        SpaceMemberEntity member = requireMember(spaceId, actor);
        if (!"ADMIN".equalsIgnoreCase(member.getRole()) && !"OPERATOR".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space access denied");
        }
    }

    @Override
    public void requireAdminSpace(Long spaceId, AuthContext actor) {
        SpaceMemberEntity member = requireMember(spaceId, actor);
        if (!"ADMIN".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space admin required");
        }
    }

    private SpaceMemberEntity requireMember(Long spaceId, AuthContext actor) {
        spaceMapper.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found: " + spaceId));
        return spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "space access denied"));
    }
}
