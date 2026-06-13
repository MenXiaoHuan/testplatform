package com.example.platform.common;

public record ApiErrorResponse(String code, Object data, String msg) {
}
