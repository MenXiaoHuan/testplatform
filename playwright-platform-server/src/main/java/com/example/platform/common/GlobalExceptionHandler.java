package com.example.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器 —— 统一将异常转换为 API 错误响应。
 *
 * <p>核心职责：
 * <ul>
 *   <li>捕获 {@link BusinessException}，按业务错误码映射为对应的 HTTP 状态码</li>
 *   <li>处理参数类异常（{@link IllegalArgumentException}、{@link IllegalStateException}）</li>
 *   <li>识别数据库唯一键冲突（{@link DataIntegrityViolationException}）并返回友好中文提示</li>
 *   <li>处理 {@link ResponseStatusException}，透传其指定的 HTTP 状态码与原因</li>
 *   <li>兜底处理所有未捕获异常，记录到 {@link ApplicationErrorSummaryService} 并返回 500</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link ApplicationErrorSummaryService}：通过 {@link ObjectProvider} 可选注入，缺失时使用空实现</li>
 *   <li>{@link ApiErrorResponse}：作为统一的错误响应体</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 错误摘要服务，可选注入；未提供时回退到空实现。 */
    private final ApplicationErrorSummaryService applicationErrorSummaryService;

    /**
     * 构造时通过 {@link ObjectProvider} 延迟获取服务，避免无该 Bean 时启动失败。
     */
    public GlobalExceptionHandler(ObjectProvider<ApplicationErrorSummaryService> applicationErrorSummaryServiceProvider) {
        this.applicationErrorSummaryService = applicationErrorSummaryServiceProvider.getIfAvailable(NoopApplicationErrorSummaryService::new);
    }

    /**
     * 处理业务异常，按错误码映射为 BAD_REQUEST 或 CONFLICT。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "BAD_REQUEST", "INVALID_PASSWORD", "ACCESS_REQUEST_LIST_FAILED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(ex.getCode(), null, ex.getMessage()));
    }

    /**
     * 处理非法参数异常，返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ApiErrorResponse("BAD_REQUEST", null, ex.getMessage());
    }

    /**
     * 处理非法状态异常，返回 409。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleIllegalState(IllegalStateException ex) {
        return new ApiErrorResponse("CONFLICT", null, ex.getMessage());
    }

    /**
     * 处理数据库完整性约束冲突，根据错误信息中的约束名返回对应的中文提示。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        // 仓库名称唯一键冲突
        if (message.contains("uk_test_repository_name") || message.contains("test_repository.name")) {
            return new ApiErrorResponse("CONFLICT", null, "仓库名称已存在，请更换后重试");
        }
        // 场景名称唯一键冲突
        if (message.contains("uk_scene_name") || message.contains("scene.name")) {
            return new ApiErrorResponse("CONFLICT", null, "场景名称已存在，请更换后重试");
        }
        // 用户名唯一键冲突
        if (message.contains("uk_platform_user_username") || message.contains("platform_user.username")) {
            return new ApiErrorResponse("USERNAME_ALREADY_EXISTS", null, "该用户名已被使用，请换一个");
        }
        // 昵称唯一键冲突
        if (message.contains("uk_platform_user_nickname") || message.contains("platform_user.nickname")) {
            return new ApiErrorResponse("NICKNAME_ALREADY_EXISTS", null, "该昵称已被使用，请换一个");
        }
        // 空间名称唯一键冲突
        if (message.contains("uk_space_name") || message.contains("space.name")) {
            return new ApiErrorResponse("SPACE_NAME_ALREADY_EXISTS", null, "空间名称已存在，请更换后重试");
        }
        return new ApiErrorResponse("CONFLICT", null, "数据保存冲突，请稍后重试");
    }

    /**
     * 处理带状态码的异常，透传其 HTTP 状态码与原因短语。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        // 若未提供原因则使用默认的状态短语
        String msg = ex.getReason() == null || ex.getReason().isBlank()
                ? status.getReasonPhrase()
                : ex.getReason();
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.name(), null, msg));
    }

    /**
     * 兜底处理所有未捕获异常，记录错误摘要并返回 500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedException(Exception ex) {
        log.error("Unhandled server exception", ex);
        // 异步记录错误摘要，供任务详情页查询
        applicationErrorSummaryService.recordError(log.getName(), ex.getMessage(), ex);
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return new ApiErrorResponse("INTERNAL_SERVER_ERROR", null, message);
        }
        return new ApiErrorResponse("INTERNAL_SERVER_ERROR", null, "Internal server error");
    }

    /**
     * 空的错误摘要服务实现，在未配置 {@link ApplicationErrorSummaryService} Bean 时作为兜底。
     */
    private static final class NoopApplicationErrorSummaryService implements ApplicationErrorSummaryService {
        @Override
        public void recordError(String loggerName, String message, Throwable throwable) {
            // 空实现：未配置摘要服务时直接忽略
        }

        @Override
        public java.util.List<com.example.platform.task.dto.ApplicationErrorSummaryResponse> listRecentForTask(
                com.example.platform.task.model.TaskEntity task,
                int limit) {
            return java.util.List.of();
        }
    }
}
