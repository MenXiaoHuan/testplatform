package com.example.platform.scene.dto;

/**
 * 调度事件重试请求 DTO —— 前端手动重试时传递的操作信息。
 *
 * @param operatorName 操作人名称（可空，默认 anonymous）
 * @param operatorId   操作人 ID（可空）
 * @param comment      重试备注（可空）
 */
public record ScheduleEventRetryRequest(
        String operatorName,
        String operatorId,
        String comment) {
}
