package com.example.platform.space.service;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.space.mapper.SpaceMapper;
import com.example.platform.space.mapper.SpaceMemberMapper;
import com.example.platform.space.model.SpaceMemberEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 空间授权服务实现类，提供空间访问权限校验功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>校验用户是否为空间成员</li>
 *   <li>校验用户是否具有读取权限（所有成员）</li>
 *   <li>校验用户是否具有操作权限（OPERATOR 或 ADMIN）</li>
 *   <li>校验用户是否具有管理员权限（仅 ADMIN）</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceMapper} - 空间数据访问接口</li>
 *   <li>{@link SpaceMemberMapper} - 空间成员数据访问接口</li>
 * </ul>
 */
@Service
public class SpaceAuthorizationServiceImpl implements SpaceAuthorizationService {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;

    public SpaceAuthorizationServiceImpl(SpaceMapper spaceMapper, SpaceMemberMapper spaceMemberMapper) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
    }

    /**
     * 校验用户是否有读取指定空间的权限
     * 要求用户为空间成员
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    @Override
    public void requireReadableSpace(Long spaceId, AuthContext actor) {
        requireMember(spaceId, actor);
    }

    /**
     * 校验用户是否有操作指定空间的权限
     * 要求用户为 OPERATOR 或 ADMIN 角色
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    @Override
    public void requireOperableSpace(Long spaceId, AuthContext actor) {
        SpaceMemberEntity member = requireMember(spaceId, actor);
        if (!"ADMIN".equalsIgnoreCase(member.getRole()) && !"OPERATOR".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space access denied");
        }
    }

    /**
     * 校验用户是否有指定空间的管理员权限
     * 要求用户为 ADMIN 角色
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     */
    @Override
    public void requireAdminSpace(Long spaceId, AuthContext actor) {
        SpaceMemberEntity member = requireMember(spaceId, actor);
        if (!"ADMIN".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "space admin required");
        }
    }

    /**
     * 校验用户是否为指定空间的活跃成员
     * 同时校验空间是否存在
     *
     * @param spaceId 空间ID
     * @param actor 当前操作用户的认证上下文
     * @return 空间成员实体
     * @throws ResponseStatusException 如果空间不存在或用户不是成员
     */
    private SpaceMemberEntity requireMember(Long spaceId, AuthContext actor) {
        // 校验空间存在性
        spaceMapper.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found: " + spaceId));
        // 校验用户是否为活跃成员
        return spaceMemberMapper.findActiveBySpaceIdAndUserId(spaceId, actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "space access denied"));
    }
}