package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.config.EvaluationProperties;
import com.example.platform.evaluation.config.LangfuseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * queryTraces 端到端单测 —— 用本地 HttpServer 模拟 Langfuse，
 * 验证「targetId 转 tag、分页 1-based、本地兜底过滤、total 一致」。
 */
class LangfuseClientImplQueryTest {

    private HttpServer server;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private LangfuseClientImpl client;

    private static final String TRACE_RESPONSE = """
            {
              "data": [
                {"id":"t1","name":"target-1","timestamp":"2026-10-29T10:00:00Z","endTime":"2026-10-29T10:00:01Z",
                 "tags":["target_id=1","env=prod"],"sessionId":"s1","input":"hi","output":"ok",
                 "inputTokens":10,"outputTokens":20,"metadata":{"foo":"bar"}},
                {"id":"t2","name":"target-2","timestamp":"2026-10-29T10:00:00Z","endTime":"2026-10-29T10:00:02Z",
                 "tags":["target_id=2"],"sessionId":"s2","input":"hello","output":"world",
                 "inputTokens":30,"outputTokens":40,"metadata":{}}
              ],
              "meta": {"page":1,"limit":50,"totalItems":2,"totalPages":1}
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/public/traces", exchange -> {
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = TRACE_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        LangfuseProperties props = new LangfuseProperties();
        props.setEnabled(true);
        props.setHost("http://localhost:" + server.getAddress().getPort());
        props.setPublicKey("pk-test");
        props.setSecretKey("sk-test");

        client = new LangfuseClientImpl(props, new LangfuseGatekeeper(new EvaluationProperties()),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldTranslateTargetIdToTagAndUseOneBasedPage() {
        Page<LangfuseTrace> page = client.queryTraces(
                LangfuseTraceFilter.builder().targetId("1").build(),
                PageRequest.of(0, 10));

        assertEquals(1, page.getNumberOfElements());
        assertEquals(1, page.getTotalElements());
        assertEquals("t1", page.getContent().get(0).traceId());
        assertEquals("target-1", page.getContent().get(0).name());

        String query = lastQuery.get();
        assertTrue(query.contains("page=1"), "Langfuse page is 1-based: " + query);
        assertTrue(query.contains("limit=10"), query);
        assertTrue(query.contains("tags=target_id%3D1") || query.contains("tags=target_id=1"),
                "targetId must be sent as tag, not targetId param: " + query);
        assertFalse(query.contains("targetId="), query);
    }

    @Test
    void shouldSendAllTagsAndFilterLocally() {
        Page<LangfuseTrace> page = client.queryTraces(
                LangfuseTraceFilter.builder().targetId("1").tags(List.of("env=prod")).build(),
                PageRequest.of(0, 10));

        assertEquals(1, page.getNumberOfElements());
        assertEquals("t1", page.getContent().get(0).traceId());

        String query = lastQuery.get();
        assertTrue(query.contains("env%3Dprod") || query.contains("env=prod"), query);
    }

    @Test
    void shouldReturnAllWhenNoTargetFilter() {
        Page<LangfuseTrace> page = client.queryTraces(
                LangfuseTraceFilter.builder().build(),
                PageRequest.of(0, 10));

        assertEquals(2, page.getNumberOfElements());
        assertEquals(2, page.getTotalElements());
        assertFalse(lastQuery.get().contains("target_id"), lastQuery.get());
    }

    @Test
    void shouldClampLimitToLangfuseMaximum() {
        client.queryTraces(LangfuseTraceFilter.builder().build(), PageRequest.of(0, 5000));

        assertTrue(lastQuery.get().contains("limit=100"),
                "Langfuse rejects limit > 100: " + lastQuery.get());
    }
}
