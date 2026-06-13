package com.example.platform.common;

public record ApiResponse<T>(String code, T data, String msg) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, "success");
    }
}
