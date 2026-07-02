package com.example.platform.space.mapper;

import com.example.platform.space.model.SpaceEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpaceMapper {
    int insert(SpaceEntity entity);

    Optional<SpaceEntity> findById(Long id);

    List<SpaceEntity> findByUserId(Long userId);
}
