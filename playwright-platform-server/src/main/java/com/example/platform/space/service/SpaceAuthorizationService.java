package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;

public interface SpaceAuthorizationService {
    void requireReadableSpace(Long spaceId, AuthContext actor);

    void requireOperableSpace(Long spaceId, AuthContext actor);

    void requireAdminSpace(Long spaceId, AuthContext actor);
}
