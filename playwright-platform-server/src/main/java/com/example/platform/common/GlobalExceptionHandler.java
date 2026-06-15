package com.example.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiErrorResponse("BAD_REQUEST", null, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIllegalState(IllegalStateException ex) {
        return new ApiErrorResponse("CONFLICT", null, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uk_test_repository_name") || message.contains("test_repository.name")) {
            return new ApiErrorResponse("CONFLICT", null, "仓库名称已存在，请更换后重试");
        }
        if (message.contains("uk_scene_name") || message.contains("scene.name")) {
            return new ApiErrorResponse("CONFLICT", null, "场景名称已存在，请更换后重试");
        }
        return new ApiErrorResponse("CONFLICT", null, "数据保存冲突，请稍后重试");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String msg = ex.getReason() == null || ex.getReason().isBlank()
                ? status.getReasonPhrase()
                : ex.getReason();
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.name(), null, msg));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedException(Exception ex) {
        log.error("Unhandled server exception", ex);
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return new ApiErrorResponse("INTERNAL_SERVER_ERROR", null, message);
        }
        return new ApiErrorResponse("INTERNAL_SERVER_ERROR", null, "Internal server error");
    }
}
