package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpaceSummaryResponse;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceEntity;
import com.example.platform.space.model.SpaceMemberEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SpaceServiceImpl implements SpaceService {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;

    public SpaceServiceImpl(SpaceMapper spaceMapper, SpaceMemberMapper spaceMemberMapper) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
    }

    @Override
    public SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request) {
        SpaceEntity entity = new SpaceEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setOwnerUserId(actor.userId());
        entity.setCreatedBy(actor.userId());
        spaceMapper.insert(entity);

        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(entity.getId());
        member.setUserId(actor.userId());
        member.setRole("ADMIN");
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());
        spaceMemberMapper.insert(member);
        return SpaceSummaryResponse.from(entity);
    }

    @Override
    public List<SpaceSummaryResponse> listMySpaces(AuthContext actor) {
        return spaceMapper.findByUserId(actor.userId()).stream()
                .map(SpaceSummaryResponse::from)
                .toList();
    }
}
