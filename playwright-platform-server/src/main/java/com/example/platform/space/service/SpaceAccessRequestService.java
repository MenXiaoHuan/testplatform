package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;

public interface SpaceAccessRequestService {
    void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request);

    void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);
}
