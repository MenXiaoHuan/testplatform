package com.example.platform.space.mapper;

import com.example.platform.space.dto.SpaceAccessRequestProjection;
import com.example.platform.space.model.SpaceAccessRequestEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpaceAccessRequestMapper {
    @Insert("""
            insert into space_access_request (
                space_id, applicant_user_id, requested_role, reason, status
            ) values (
                #{spaceId}, #{applicantUserId}, #{requestedRole}, #{reason}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceAccessRequestEntity entity);

    @Select("""
            select id, space_id, applicant_user_id, requested_role, reason, status,
                   review_comment, reviewed_by, reviewed_at, created_at, updated_at
            from space_access_request
            where id = #{id}
            """)
    @Results(id = "SpaceAccessRequestResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "applicantUserId", column = "applicant_user_id"),
            @Result(property = "requestedRole", column = "requested_role"),
            @Result(property = "reason", column = "reason"),
            @Result(property = "status", column = "status"),
            @Result(property = "reviewComment", column = "review_comment"),
            @Result(property = "reviewedBy", column = "reviewed_by"),
            @Result(property = "reviewedAt", column = "reviewed_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<SpaceAccessRequestEntity> findById(Long id);

    @Select("""
            select id, space_id, applicant_user_id, requested_role, reason, status,
                   review_comment, reviewed_by, reviewed_at, created_at, updated_at
            from space_access_request
            where space_id = #{spaceId}
              and applicant_user_id = #{applicantUserId}
              and status = 'PENDING'
            order by id desc
            limit 1
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceAccessRequestResultMap")
    Optional<SpaceAccessRequestEntity> findPendingBySpaceIdAndApplicantUserId(Long spaceId, Long applicantUserId);

    @Select("""
            select id, space_id, applicant_user_id, requested_role, reason, status,
                   review_comment, reviewed_by, reviewed_at, created_at, updated_at
            from space_access_request
            where space_id = #{spaceId}
            order by created_at desc, id desc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceAccessRequestResultMap")
    List<SpaceAccessRequestEntity> findBySpaceId(Long spaceId);

    @Select("""
            select sar.id,
                   sar.space_id,
                   sar.applicant_user_id,
                   pu.username as applicant_username,
                   pu.nickname as applicant_nickname,
                   pu.avatar_object_key as applicant_avatar_object_key,
                   sar.requested_role,
                   sar.reason,
                   sar.status,
                   sar.review_comment,
                   sar.reviewed_by,
                   sar.reviewed_at,
                   sar.created_at,
                   sar.updated_at
            from space_access_request sar
            join platform_user pu on pu.id = sar.applicant_user_id
            where sar.space_id = #{spaceId}
            order by sar.created_at desc, sar.id desc
            """)
    @Results(id = "SpaceAccessRequestProjectionResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "spaceId", column = "space_id"),
            @Result(property = "applicantUserId", column = "applicant_user_id"),
            @Result(property = "applicantUsername", column = "applicant_username"),
            @Result(property = "applicantNickname", column = "applicant_nickname"),
            @Result(property = "applicantAvatarObjectKey", column = "applicant_avatar_object_key"),
            @Result(property = "requestedRole", column = "requested_role"),
            @Result(property = "reason", column = "reason"),
            @Result(property = "status", column = "status"),
            @Result(property = "reviewComment", column = "review_comment"),
            @Result(property = "reviewedBy", column = "reviewed_by"),
            @Result(property = "reviewedAt", column = "reviewed_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<SpaceAccessRequestProjection> findProjectionBySpaceId(Long spaceId);

    @Update("""
            update space_access_request
            set status = #{status},
                review_comment = #{reviewComment},
                reviewed_by = #{reviewedBy},
                reviewed_at = current_timestamp
            where id = #{requestId}
            """)
    int updateReview(
            @Param("requestId") Long requestId,
            @Param("status") String status,
            @Param("reviewComment") String reviewComment,
            @Param("reviewedBy") Long reviewedBy);

    @Delete("""
            delete from space_access_request
            where space_id = #{spaceId}
            """)
    int deleteBySpaceId(@Param("spaceId") Long spaceId);
}
