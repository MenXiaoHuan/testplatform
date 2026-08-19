package com.example.platform.space.dto;

/**
 * 提交空间访问申请请求 DTO。
 * 用于用户申请加入某个空间时提交申请信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装申请的角色</li>
 *   <li>封装申请理由</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 *
 * @param requestedRole 申请的角色（如 ADMIN、OPERATOR、VIEWER）
 * @param reason 申请理由
 */
public record SubmitSpaceAccessRequestRequest(
        String requestedRole,
        String reason) {
}