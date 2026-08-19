package com.example.platform.common;

/**
 * API 成功响应封装 —— 统一的接口成功响应结构。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装成功态的统一响应格式，携带业务数据与描述信息</li>
 *   <li>提供 {@link #ok(Object)} 与 {@link #error(String, String)} 工厂方法，快速构造响应</li>
 * </ul>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code code}：业务状态码，成功时为 "OK"</li>
 *   <li>{@code data}：泛型业务数据</li>
 *   <li>{@code msg}：响应描述信息</li>
 * </ul>
 *
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(String code, T data, String msg) {

    /**
     * 构造成功响应，使用 "OK" 码和默认 "success" 消息。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, "success");
    }

    /**
     * 构造带错误码和消息的响应（通常用于业务层返回非成功状态）。
     */
    public static <T> ApiResponse<T> error(String code, String msg) {
        return new ApiResponse<>(code, null, msg);
    }
}
