package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.SpaceAccessRequestResponse;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import java.util.List;

public interface SpaceAccessRequestService {
    void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request);

    List<SpaceAccessRequestResponse> listBySpace(Long spaceId, AuthContext actor);

    void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);

    void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);
}
