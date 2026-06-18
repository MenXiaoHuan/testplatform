package com.example.platform.common;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(
                items,
                total,
                page,
                size,
                totalPages,
                page < totalPages,
                page > 1);
    }

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
