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

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_KEY = "requestId";
    static final String TRACE_ID_KEY = "traceId";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalizeHeader(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        String traceId = normalizeHeader(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = requestId;
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try (MDC.MDCCloseable requestIdCloseable = MDC.putCloseable(REQUEST_ID_KEY, requestId);
             MDC.MDCCloseable traceIdCloseable = MDC.putCloseable(TRACE_ID_KEY, traceId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
