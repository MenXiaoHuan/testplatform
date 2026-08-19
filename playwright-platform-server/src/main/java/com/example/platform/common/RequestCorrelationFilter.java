package com.example.platform.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求关联过滤器 —— 为每个 HTTP 请求注入 requestId 与 traceId。
 *
 * <p>核心职责：
 * <ul>
 *   <li>读取或生成 {@code X-Request-Id} 与 {@code X-Trace-Id} 请求头</li>
 *   <li>将两个 ID 写入响应头，便于调用方追踪</li>
 *   <li>通过 SLF4J {@link MDC} 绑定到当前线程，使日志自动携带链路标识</li>
 * </ul>
 *
 * <p>ID 生成规则：
 * <ul>
 *   <li>若请求头中已提供则直接使用；否则生成随机 UUID 作为 requestId</li>
 *   <li>若未提供 traceId，则回退为 requestId</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>SLF4J MDC 用于日志上下文透传</li>
 *   <li>Spring {@link OncePerRequestFilter} 保证每个请求仅过滤一次</li>
 * </ul>
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /** MDC 中 requestId 的键名。 */
    static final String REQUEST_ID_KEY = "requestId";

    /** MDC 中 traceId 的键名。 */
    static final String TRACE_ID_KEY = "traceId";

    /** HTTP 请求头/响应头中 requestId 的名称。 */
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** HTTP 请求头/响应头中 traceId 的名称。 */
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 过滤请求：生成/读取链路 ID，写入 MDC 与响应头后继续执行后续过滤器。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 尝试从请求头读取 requestId，缺失则生成新的 UUID
        String requestId = normalizeHeader(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        // 尝试从请求头读取 traceId，缺失则回退为 requestId
        String traceId = normalizeHeader(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = requestId;
        }

        // 将链路 ID 写回响应头，便于调用方获取
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        // 通过 try-with-resources 确保 MDC 上下文在请求结束后自动清理，避免线程复用污染
        try (MDC.MDCCloseable requestIdCloseable = MDC.putCloseable(REQUEST_ID_KEY, requestId);
             MDC.MDCCloseable traceIdCloseable = MDC.putCloseable(TRACE_ID_KEY, traceId)) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 规范化请求头值：去除首尾空白，空字符串视为缺失。
     */
    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
