package com.example.platform.repository.mapper;

import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface TestRepositoryMapper {
    int insert(TestRepositoryEntity entity);

    int update(TestRepositoryEntity entity);

    Optional<TestRepositoryEntity> findById(@Param("id") Long id);

    List<TestRepositoryEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    boolean existsByNameIgnoreCase(@Param("name") String name);

    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
