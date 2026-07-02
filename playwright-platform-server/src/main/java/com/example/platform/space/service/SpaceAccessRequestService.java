package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.dto.ReviewSpaceAccessRequestRequest;
import com.example.platform.space.dto.SubmitSpaceAccessRequestRequest;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import java.util.List;

public interface SpaceAccessRequestService {
    void submitRequest(AuthContext actor, Long spaceId, SubmitSpaceAccessRequestRequest request);

    List<SpaceAccessRequestEntity> listBySpace(Long spaceId, AuthContext actor);

    void approveRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);

    void rejectRequest(AuthContext actor, Long spaceId, Long requestId, ReviewSpaceAccessRequestRequest request);
}
