package com.example.platform.evaluation.langfuse;

import com.example.platform.evaluation.config.EvaluationProperties;
import com.example.platform.evaluation.config.LangfuseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace 详情单测 —— 验证 Langfuse GET /api/public/traces/{id} 的解析：
 * Trace 的 JSON input/output、Observation 父子链路、usage token 汇总与 ERROR 识别。
 */
class LangfuseClientImplTraceDetailTest {

    private static final String DETAIL_RESPONSE = """
            {
              "id":"t1","name":"agent-run","timestamp":"2026-10-30T04:00:00Z",
              "tags":["target_id=2","space_id=1"],
              "input":{"question":"ping"},"output":{"answer":"pong"},
              "metadata":{"channel":"web"},
              "latency":0.25,
              "observations":[
                {"id":"o1","traceId":"t1","parentObservationId":null,"type":"GENERATION","name":"llm",
                 "model":"gpt-test","startTime":"2026-10-30T04:00:00Z","endTime":"2026-10-30T04:00:00.250Z",
                 "latency":0.25,"level":"DEFAULT",
                 "input":[{"role":"user","content":"ping"}],
                 "output":{"role":"assistant","content":"pong"},
                 "usageDetails":{"input":10,"output":20,"total":30}},
                {"id":"o2","traceId":"t1","parentObservationId":"o1","type":"SPAN","name":"tool",
                 "startTime":"2026-10-30T04:00:00Z","endTime":"2026-10-30T04:00:00.100Z",
                 "latency":0.1,"level":"ERROR","statusMessage":"tool failed",
                 "input":{"city":"sh"},"output":{"error":"timeout"}}
              ]
            }
            """;

    private HttpServer server;
    private LangfuseClientImpl client;
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/public/traces", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            if (exchange.getRequestURI().getPath().endsWith("/t1")) {
                byte[] body = DETAIL_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
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
        if (server != null) server.stop(0);
    }

    @Test
    void shouldParseTraceAndObservationTree() {
        Optional<LangfuseTraceDetail> result = client.getTraceDetail("t1");
        assertTrue(result.isPresent());

        LangfuseTraceDetail detail = result.get();
        LangfuseTrace trace = detail.trace();
        assertEquals("t1", trace.traceId());
        assertEquals("agent-run", trace.name());
        assertEquals("2", trace.targetId());
        // input/output 是 JSON 对象，必须序列化成字符串而不是空串
        assertEquals("{\"question\":\"ping\"}", trace.input());
        assertEquals("{\"answer\":\"pong\"}", trace.output());
        assertEquals("web", trace.metadata().get("channel"));
        // 列表不带 usage，详情用 observation 汇总
        assertEquals(10L, trace.inputTokens());
        assertEquals(20L, trace.outputTokens());
        // 任一 observation ERROR → trace 失败
        assertFalse(trace.success());

        assertEquals(2, detail.observations().size());
        LangfuseObservation generation = detail.observations().get(0);
        assertEquals("GENERATION", generation.type());
        assertEquals("gpt-test", generation.model());
        assertNull(generation.parentObservationId());
        assertEquals(10L, generation.inputTokens());
        assertEquals(20L, generation.outputTokens());
        assertEquals(30L, generation.totalTokens());
        assertEquals(250L, generation.latencyMs());
        assertNotNull(generation.input());
        assertTrue(generation.input().contains("ping"));

        LangfuseObservation span = detail.observations().get(1);
        assertEquals("o1", span.parentObservationId());
        assertEquals("ERROR", span.level());
        assertEquals("tool failed", span.statusMessage());
        assertEquals(100L, span.latencyMs());

        assertEquals("/api/public/traces/t1", lastPath.get());
    }

    @Test
    void shouldReturnEmptyWhenTraceNotFound() {
        assertTrue(client.getTraceDetail("missing").isEmpty());
        assertTrue(client.getTrace("missing").isEmpty());
    }
}
