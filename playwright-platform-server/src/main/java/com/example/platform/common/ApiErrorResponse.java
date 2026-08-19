package com.example.platform.common;

/**
 * API 错误响应封装 —— 统一的错误态响应结构。
 *
 * <p>核心职责：
 * <ul>
 *   <li>以不可变记录（record）形式承载错误码、附加数据与错误消息</li>
 *   <li>与 {@link ApiResponse} 并列使用，用于异常处理器向调用方返回标准化错误信息</li>
 * </ul>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code code}：业务错误码，如 BAD_REQUEST、CONFLICT 等</li>
 *   <li>{@code data}：附加数据（通常为 null，预留扩展）</li>
 *   <li>{@code msg}：面向调用方的可读错误描述</li>
 * </ul>
 */
public record ApiErrorResponse(String code, Object data, String msg) {
}
