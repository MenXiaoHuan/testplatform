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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 写入路径单测 —— 验证 Langfuse v3 统一走 /api/public/ingestion 批量事件。 */
class LangfuseClientImplWriteTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private LangfuseClientImpl client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/public/ingestion", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"successes\":[],\"errors\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(207, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
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
    void shouldWriteTraceThroughIngestionBatch() {
        client.writeTrace(TraceWriteRequest.builder()
                .traceId("trace-1").name("agent-request")
                .tags(List.of("target_id=1"))
                .timestamp(Instant.now())
                .build());

        String body = lastBody.get();
        assertTrue(body != null && body.contains("\"batch\""), "batch envelope expected: " + body);
        assertTrue(body.contains("\"type\":\"trace-create\""), body);
        assertTrue(body.contains("\"id\":\"trace-1\""), body);
    }

    @Test
    void shouldWriteGenerationWhenModelPresent() {
        client.writeObservation(ObservationWriteRequest.builder()
                .traceId("trace-1").observationId("span-1").name("llm-call")
                .model("gpt-4o-mini")
                .inputTokens(10L).outputTokens(20L).totalTokens(30L)
                .startTime(Instant.now()).endTime(Instant.now())
                .build());

        String body = lastBody.get();
        assertTrue(body.contains("\"type\":\"generation-create\""), body);
        assertTrue(body.contains("\"traceId\":\"trace-1\""), body);
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""), body);
    }

    @Test
    void shouldWriteSpanWhenNoModel() {
        client.writeObservation(ObservationWriteRequest.builder()
                .traceId("trace-1").observationId("span-2").name("tool-call")
                .startTime(Instant.now()).endTime(Instant.now())
                .build());

        String body = lastBody.get();
        assertTrue(body.contains("\"type\":\"span-create\""), body);
    }
}
