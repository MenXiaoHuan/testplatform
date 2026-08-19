package com.example.platform.task.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 任务时间映射工具类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>提供时间类型转换工具方法</li>
 *   <li>将 {@link LocalDateTime} 转换为 {@link Instant}（UTC时区）</li>
 * </ul>
 *
 * <p>依赖：{@link LocalDateTime}、{@link Instant}、{@link ZoneOffset}
 */
final class TaskTimeMapper {

    /**
     * 私有构造函数，防止实例化
     */
    private TaskTimeMapper() {
    }

    /**
     * 将 LocalDateTime 转换为 Instant（UTC时区）
     *
     * @param value 本地日期时间（可为null）
     * @return Instant 对象（UTC时区），若输入为null则返回null
     */
    static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
