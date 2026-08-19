package com.example.platform.common;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应封装 —— 通用的分页结果承载结构。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装分页查询的结果集、总数、页码及分页元信息</li>
 *   <li>提供 {@link #of(List, long, int, int)} 工厂方法自动计算总页数与前后页标识</li>
 *   <li>提供 {@link #map(Function)} 方法，支持在保持分页元数据不变的情况下转换元素类型</li>
 * </ul>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code items}：当前页数据列表</li>
 *   <li>{@code total}：总记录数</li>
 *   <li>{@code page}：当前页码（从 1 开始）</li>
 *   <li>{@code size}：每页大小</li>
 *   <li>{@code totalPages}：总页数</li>
 *   <li>{@code hasNext}：是否存在下一页</li>
 *   <li>{@code hasPrevious}：是否存在上一页</li>
 * </ul>
 *
 * @param <T> 数据项类型
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    /**
     * 根据给定的列表与分页参数构造分页响应，并自动计算总页数、前后页标识。
     *
     * @param items 当前页数据
     * @param total 总记录数
     * @param page  当前页码（从 1 开始）
     * @param size  每页大小
     * @param <T>   数据项类型
     */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        // 根据总记录数和每页大小向上取整计算总页数
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                items,
                total,
                page,
                size,
                totalPages,
                // 当前页小于总页数表示存在下一页
                page < totalPages,
                // 当前页大于 1 表示存在上一页
                page > 1);
    }

    /**
     * 将当前分页响应中的每个元素映射为新类型，保留分页元数据不变。
     *
     * @param mapper 元素转换函数
     * @param <R>    目标元素类型
     */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(
                items.stream().map(mapper).toList(),
                total,
                page,
                size,
                totalPages,
                hasNext,
                hasPrevious);
    }
}
