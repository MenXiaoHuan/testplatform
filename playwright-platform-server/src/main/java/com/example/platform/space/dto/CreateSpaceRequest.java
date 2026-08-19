package com.example.platform.space.dto;

/**
 * 创建/更新空间请求 DTO。
 * 用于接收前端提交的空间创建或更新请求参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装空间名称</li>
 *   <li>封装空间描述信息</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 *
 * @param name 空间名称
 * @param description 空间描述
 */
public record CreateSpaceRequest(
        String name,
        String description) {
}