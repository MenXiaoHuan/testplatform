package com.example.platform.space.dto;

/**
 * 审批空间访问请求请求 DTO。
 * 用于管理员对访问申请进行批准或拒绝时提交审批意见。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装审批评论/意见</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 *
 * @param reviewComment 审批评论内容
 */
public record ReviewSpaceAccessRequestRequest(
        String reviewComment) {
}