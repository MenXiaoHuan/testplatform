package com.example.platform.evaluation.langfuse;

import java.time.Instant;
import java.util.List;

/** Langfuse Trace 查询过滤条件（C12 只读 Key 查询在线 Trace + 监控聚合共用）。 */
public record LangfuseTraceFilter(
        String targetId,
        Instant startTimeFrom,
        Instant startTimeTo,
        List<String> tags,
        List<String> sessionIds,
        Boolean onlyFailed,
        Boolean onlySuccess,
        String searchQuery,
        String userId) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String targetId;
        private Instant startTimeFrom;
        private Instant startTimeTo;
        private List<String> tags;
        private List<String> sessionIds;
        private Boolean onlyFailed;
        private Boolean onlySuccess;
        private String searchQuery;
        private String userId;

        public Builder targetId(String v) { this.targetId = v; return this; }
        public Builder startTimeFrom(Instant v) { this.startTimeFrom = v; return this; }
        public Builder startTimeTo(Instant v) { this.startTimeTo = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder sessionIds(List<String> v) { this.sessionIds = v; return this; }
        public Builder onlyFailed(Boolean v) { this.onlyFailed = v; return this; }
        public Builder onlySuccess(Boolean v) { this.onlySuccess = v; return this; }
        public Builder searchQuery(String v) { this.searchQuery = v; return this; }
        public Builder userId(String v) { this.userId = v; return this; }

        public LangfuseTraceFilter build() {
            return new LangfuseTraceFilter(targetId, startTimeFrom, startTimeTo,
                    tags, sessionIds, onlyFailed, onlySuccess, searchQuery, userId);
        }
    }
}
