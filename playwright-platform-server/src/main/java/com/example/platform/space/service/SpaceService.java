package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpacePlazaResponse;
import com.example.platform.space.dto.SpaceSummaryResponse;
import java.util.List;

public interface SpaceService {
    SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request);
    SpaceSummaryResponse updateSpace(AuthContext actor, Long spaceId, CreateSpaceRequest request);
    void deleteSpace(AuthContext actor, Long spaceId);

    List<SpaceSummaryResponse> listMySpaces(AuthContext actor);
    List<SpacePlazaResponse> listSpacePlaza(AuthContext actor);
}
