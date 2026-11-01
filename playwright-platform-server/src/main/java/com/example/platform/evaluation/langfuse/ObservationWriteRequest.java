package com.example.platform.evaluation.langfuse;

import java.time.Instant;
import java.util.Map;

/** 写入 Langfuse Observation（Span）的请求 DTO。 */
public record ObservationWriteRequest(
        String traceId,
        String observationId,
        String parentObservationId,
        String name,
        String input,
        String output,
        Instant startTime,
        Instant endTime,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        String model,
        boolean success,
        String level,
        Map<String, Object> metadata) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String traceId;
        private String observationId;
        private String parentObservationId;
        private String name;
        private String input;
        private String output;
        private Instant startTime;
        private Instant endTime;
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private String model;
        private boolean success = true;
        private String level;
        private Map<String, Object> metadata;

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder observationId(String v) { this.observationId = v; return this; }
        public Builder parentObservationId(String v) { this.parentObservationId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder input(String v) { this.input = v; return this; }
        public Builder output(String v) { this.output = v; return this; }
        public Builder startTime(Instant v) { this.startTime = v; return this; }
        public Builder endTime(Instant v) { this.endTime = v; return this; }
        public Builder inputTokens(Long v) { this.inputTokens = v; return this; }
        public Builder outputTokens(Long v) { this.outputTokens = v; return this; }
        public Builder totalTokens(Long v) { this.totalTokens = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder success(boolean v) { this.success = v; return this; }
        public Builder level(String v) { this.level = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public ObservationWriteRequest build() {
            return new ObservationWriteRequest(traceId, observationId, parentObservationId,
                    name, input, output, startTime, endTime, inputTokens, outputTokens,
                    totalTokens, model, success, level, metadata);
        }
    }
}
