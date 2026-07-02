package com.example.platform.space.mapper;

import com.example.platform.space.model.SpaceMemberEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpaceMemberMapper {
    int insert(SpaceMemberEntity entity);

    Optional<SpaceMemberEntity> findActiveBySpaceIdAndUserId(Long spaceId, Long userId);

    List<SpaceMemberEntity> findBySpaceId(Long spaceId);

    int updateStatus(Long spaceId, Long userId, String status);
}
