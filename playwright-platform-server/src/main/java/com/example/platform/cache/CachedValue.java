package com.example.platform.cache;

/**
 * 缓存值封装 —— 区分缓存命中的有效值与空值占位。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #hit(Object)} —— 创建一个缓存命中的值（{@code empty=false}）</li>
 *   <li>{@link #nullValue()} —— 创建一个缓存空值的占位（{@code empty=true}），用于防止缓存穿透</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>无外部依赖，为纯 Java Record 类型</li>
 * </ul>
 */
public record CachedValue(boolean empty, Object value) {

    /**
     * 创建一个缓存命中的值。
     *
     * @param value 实际缓存的对象值
     * @return 标记为非空的 {@code CachedValue} 实例
     */
    public static CachedValue hit(Object value) {
        return new CachedValue(false, value);
    }

    /**
     * 创建一个缓存空值的占位实例。
     *
     * @return 标记为空的 {@code CachedValue} 实例
     */
    public static CachedValue nullValue() {
        return new CachedValue(true, null);
    }
}
