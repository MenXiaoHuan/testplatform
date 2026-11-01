package com.example.platform.evaluation.prompt;

import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.exception.PromptNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Prompt 拉取服务 —— Agent 调用前的 prompt 解析入口。
 * 内部走 {@link PromptCache} Caffeine 本地缓存（C23），miss 再查 DB。
 * 负责生成 ETag 供 If-None-Match 协商缓存（P95 ≤ 10ms 要求）。
 */
@Service
public class PromptPullService {

    private static final Logger log = LoggerFactory.getLogger(PromptPullService.class);

    private final EvalPromptVersionMapper mapper;
    private final PromptCache cache;

    public PromptPullService(EvalPromptVersionMapper mapper, PromptCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    /** 拉取 ACTIVE 版本；etag 匹配返回 NOT_MODIFIED 信号。 */
    public PromptPullResult pullActive(String promptKey, Long spaceId, String etag) {
        EvalPromptVersionEntity version = cache.getActive(spaceId, promptKey, () ->
                mapper.findByPromptKeyAndStatus(spaceId, promptKey, PromptStatus.ACTIVE.name())
                        .orElse(null));

        if (version == null) {
            throw new PromptNotFoundException("promptKey=" + promptKey + " 无 ACTIVE 版本（spaceId=" + spaceId + "）");
        }
        String computedEtag = computeEtag(version);
        if (normalizeEtag(etag) != null && normalizeEtag(etag).equals(computedEtag)) {
            return PromptPullResult.notModified(computedEtag);
        }
        return PromptPullResult.ok(version, computedEtag);
    }

    /** HTTP ETag 头通常带双引号（Tomcat 会自动加），比较前统一去掉。 */
    private static String normalizeEtag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** 拉取指定版本；无 ACTIVE 唯一约束。 */
    public PromptPullResult pullVersion(String promptKey, Long spaceId, String versionLabel) {
        EvalPromptVersionEntity version = cache.getVersion(spaceId, promptKey, versionLabel, () ->
                mapper.findByPromptKeyAndVersion(spaceId, promptKey, versionLabel).orElse(null));

        if (version == null) {
            throw new PromptNotFoundException("promptKey=" + promptKey + ", versionLabel=" + versionLabel + " 不存在");
        }
        return PromptPullResult.ok(version, computeEtag(version));
    }

    private static String computeEtag(EvalPromptVersionEntity v) {
        // 基于 versionLabel + updatedAt 内容的组合 etag（非加密 hash 够用）
        String raw = v.getVersionLabel() + "|" + v.getUpdatedAt();
        int hash = 0;
        for (byte b : raw.getBytes()) hash = 31 * hash + b;
        return "W/" + v.getVersionLabel() + ":hash:" + Integer.toHexString(hash);
    }

    /** 拉取结果 —— 区分 OK / NOT_MODIFIED。 */
    public record PromptPullResult(
            Status status,
            EvalPromptVersionEntity version,
            String etag) {

        public enum Status { OK, NOT_MODIFIED }

        public static PromptPullResult ok(EvalPromptVersionEntity v, String etag) {
            return new PromptPullResult(Status.OK, v, etag);
        }
        public static PromptPullResult notModified(String etag) {
            return new PromptPullResult(Status.NOT_MODIFIED, null, etag);
        }

        public boolean isOk() { return status == Status.OK; }
    }
}
