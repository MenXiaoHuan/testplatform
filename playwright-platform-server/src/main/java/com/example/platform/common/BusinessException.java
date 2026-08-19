package com.example.platform.common;

/**
 * 业务异常 —— 携带业务错误码的运行时异常。
 *
 * <p>核心职责：
 * <ul>
 *   <li>在业务流程中抛出，由 {@link GlobalExceptionHandler} 统一捕获并映射为 HTTP 响应</li>
 *   <li>通过错误码 {@code code} 区分不同业务场景（如 BAD_REQUEST、CONFLICT 等）</li>
 * </ul>
 *
 * <p>依赖关系：
 * <ul>
 *   <li>被 {@link GlobalExceptionHandler} 捕获处理</li>
 *   <li>错误码常量与 {@link GlobalExceptionHandler} 中的映射 switch 保持一致</li>
 * </ul>
 */
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码，用于异常处理器进行 HTTP 状态码映射。
     */
    private final String code;

    /**
     * 构造业务异常。
     *
     * @param code    业务错误码
     * @param message 可读错误描述
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     */
    public String getCode() {
        return code;
    }
}
