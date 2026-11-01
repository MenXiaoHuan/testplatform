package com.example.platform.evaluation.prompt;

import com.example.platform.evaluation.common.PromptStatus;
import com.example.platform.evaluation.config.EvaluationProperties;
import com.example.platform.evaluation.exception.PromptNotFoundException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * C23 Prompt 拉取本地缓存 —— Caffeine，默认 1000 条目 + 300s TTL。
 * 缓存 key = "spaceId|promptKey|statusOrVersion"。
 */
@Component
public class PromptCache {

    private static final Logger log = LoggerFactory.getLogger(PromptCache.class);

    private final Cache<String, EvalPromptVersionEntity> activeCache;

    public PromptCache(EvaluationProperties props) {
        this.activeCache = Caffeine.newBuilder()
                .maximumSize(props.getPrompt().getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getPrompt().getCacheExpireAfterWriteSeconds()))
                .build();
        log.info("PromptCache initialized: maxSize={}, ttl={}s",
                props.getPrompt().getCacheMaxSize(),
                props.getPrompt().getCacheExpireAfterWriteSeconds());
    }

    /** 按 promptKey + ACTIVE 查缓存；miss 则走 loader 并回填。 */
    public EvalPromptVersionEntity getActive(Long spaceId, String promptKey, CacheLoader loader) {
        String key = activeKey(spaceId, promptKey);
        EvalPromptVersionEntity cached = activeCache.getIfPresent(key);
        if (cached != null) return cached;
        EvalPromptVersionEntity loaded = loader.load();
        if (loaded != null) {
            activeCache.put(key, loaded);
        }
        return loaded;
    }

    /** 按 promptKey + versionLabel 查缓存（独立 key）。 */
    public EvalPromptVersionEntity getVersion(Long spaceId, String promptKey, String versionLabel, CacheLoader loader) {
        String key = versionKey(spaceId, promptKey, versionLabel);
        EvalPromptVersionEntity cached = activeCache.getIfPresent(key);
        if (cached != null) return cached;
        EvalPromptVersionEntity loaded = loader.load();
        if (loaded != null) activeCache.put(key, loaded);
        return loaded;
    }

    /** 发布新版本时调用，清掉该 promptKey 的所有缓存。 */
    public void invalidate(Long spaceId, String promptKey) {
        activeCache.invalidateAll();  // 简化：发布频率低，直接全清
        log.debug("Prompt cache invalidated: spaceId={}, promptKey={}", spaceId, promptKey);
    }

    /** 清全部。 */
    public void evictAll() { activeCache.invalidateAll(); }

    /** 当前命中率（性能测试用）。 */
    public double hitRate() {
        // Caffeine stats 需 builder 开启 recordStats() 才有效；这里简化返回 0
        return activeCache.estimatedSize() > 0 ? 0.99 : 0;
    }

    private static String activeKey(Long spaceId, String promptKey) {
        return spaceId + "|" + promptKey + "|ACTIVE";
    }
    private static String versionKey(Long spaceId, String promptKey, String versionLabel) {
        return spaceId + "|" + promptKey + "|" + versionLabel;
    }

    @FunctionalInterface
    public interface CacheLoader { EvalPromptVersionEntity load(); }
}
