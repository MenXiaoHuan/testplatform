package com.example.platform.space.mapper;

import com.example.platform.space.model.SpaceAccessRequestEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpaceAccessRequestMapper {
    int insert(SpaceAccessRequestEntity entity);

    Optional<SpaceAccessRequestEntity> findById(Long id);

    Optional<SpaceAccessRequestEntity> findPendingBySpaceIdAndApplicantUserId(Long spaceId, Long applicantUserId);

    List<SpaceAccessRequestEntity> findBySpaceId(Long spaceId);

    int updateReview(Long requestId, String status, String reviewComment, Long reviewedBy);
}
