package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.CreateSpaceRequest;
import com.example.platform.space.dto.SpaceSummaryResponse;
import java.util.List;

public interface SpaceService {
    SpaceSummaryResponse createSpace(AuthContext actor, CreateSpaceRequest request);

    List<SpaceSummaryResponse> listMySpaces(AuthContext actor);
}
