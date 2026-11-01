package com.example.platform.evaluation.langfuse;

/** 写入 Langfuse Score 的请求 DTO。 */
public record ScoreWriteRequest(
        String traceId,
        String name,
        Double value,
        String comment,
        String dataType,
        String dimension,
        String observationId) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String traceId;
        private String name;
        private Double value;
        private String comment;
        private String dataType = "NUMERIC";
        private String dimension;
        private String observationId;

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder value(Double v) { this.value = v; return this; }
        public Builder comment(String v) { this.comment = v; return this; }
        public Builder dataType(String v) { this.dataType = v; return this; }
        public Builder dimension(String v) { this.dimension = v; return this; }
        public Builder observationId(String v) { this.observationId = v; return this; }

        public ScoreWriteRequest build() {
            return new ScoreWriteRequest(traceId, name, value, comment,
                    dataType, dimension, observationId);
        }
    }
}
