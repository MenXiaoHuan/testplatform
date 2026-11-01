package com.example.platform.evaluation.langfuse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 写入 Langfuse Trace 的请求 DTO。Builder 风格。 */
public record TraceWriteRequest(
        String traceId,
        String name,
        String sessionId,
        String userId,
        String input,
        String output,
        List<String> tags,
        Map<String, Object> metadata,
        Instant timestamp) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String traceId;
        private String name;
        private String sessionId;
        private String userId;
        private String input;
        private String output;
        private List<String> tags;
        private Map<String, Object> metadata;
        private Instant timestamp = Instant.now();

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }
        public Builder input(String v) { this.input = v; return this; }
        public Builder output(String v) { this.output = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }

        public TraceWriteRequest build() {
            return new TraceWriteRequest(traceId, name, sessionId, userId, input, output,
                    tags, metadata, timestamp);
        }
    }
}
