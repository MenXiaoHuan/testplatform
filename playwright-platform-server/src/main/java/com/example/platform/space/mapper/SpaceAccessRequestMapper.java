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

/**
 * 空间访问申请数据访问接口，提供访问申请的 CRUD 操作。
 *
 * <p>核心职责：
 * <ul>
 *   <li>新增访问申请记录</li>
 *   <li>根据ID查询访问申请</li>
 *   <li>查询指定用户在指定空间的待处理申请</li>
 *   <li>查询指定空间的所有访问申请</li>
 *   <li>查询指定空间的访问申请列表（含申请人信息）</li>
 *   <li>更新申请审批结果</li>
 *   <li>删除指定空间的所有访问申请</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SpaceAccessRequestEntity} - 访问申请实体类</li>
 *   <li>{@link SpaceAccessRequestProjection} - 访问申请投影类</li>
 *   <li>MyBatis - ORM 框架</li>
 * </ul>
 */
@Mapper
public interface SpaceAccessRequestMapper {
    
    /**
     * 插入新的访问申请记录
     *
     * @param entity 访问申请实体
     * @return 受影响的行数
     */
    @Insert("""
            insert into space_access_request (
                space_id, applicant_user_id, requested_role, reason, status
            ) values (
                #{spaceId}, #{applicantUserId}, #{requestedRole}, #{reason}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SpaceAccessRequestEntity entity);

    /**
     * 根据ID查询访问申请
     *
     * @param id 申请ID
     * @return 访问申请实体（可选）
     */
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

    /**
     * 查询指定用户在指定空间的待处理申请
     *
     * @param spaceId 空间ID
     * @param applicantUserId 申请人用户ID
     * @return 访问申请实体（可选）
     */
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

    /**
     * 查询指定空间的所有访问申请
     *
     * @param spaceId 空间ID
     * @return 访问申请实体列表
     */
    @Select("""
            select id, space_id, applicant_user_id, requested_role, reason, status,
                   review_comment, reviewed_by, reviewed_at, created_at, updated_at
            from space_access_request
            where space_id = #{spaceId}
            order by created_at desc, id desc
            """)
    @org.apache.ibatis.annotations.ResultMap("SpaceAccessRequestResultMap")
    List<SpaceAccessRequestEntity> findBySpaceId(Long spaceId);

    /**
     * 查询指定空间的访问申请列表，包含申请人的用户名、昵称和头像信息
     *
     * @param spaceId 空间ID
     * @return 访问申请投影列表
     */
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

    /**
     * 更新访问申请的审批结果
     *
     * @param requestId 申请ID
     * @param status 新状态（APPROVED 或 REJECTED）
     * @param reviewComment 审批评论
     * @param reviewedBy 审批人用户ID
     * @return 受影响的行数
     */
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

    /**
     * 删除指定空间的所有访问申请
     *
     * @param spaceId 空间ID
     * @return 受影响的行数
     */
    @Delete("""
            delete from space_access_request
            where space_id = #{spaceId}
            """)
    int deleteBySpaceId(@Param("spaceId") Long spaceId);
}