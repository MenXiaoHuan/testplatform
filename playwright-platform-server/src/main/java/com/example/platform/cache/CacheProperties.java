package com.example.platform.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.cache")
public class CacheProperties {
    private Duration detailTtl = Duration.ofMinutes(5);
    private Duration nullTtl = Duration.ofMinutes(1);
    private int jitterSeconds = 60;
    private Duration mutexTtl = Duration.ofSeconds(5);
    private int lockRetryTimes = 3;
    private long lockWaitMillis = 50;

    public Duration getDetailTtl() {
        return detailTtl;
    }

    public void setDetailTtl(Duration detailTtl) {
        this.detailTtl = detailTtl;
    }

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = nullTtl;
    }

    public int getJitterSeconds() {
        return jitterSeconds;
    }

    public void setJitterSeconds(int jitterSeconds) {
        this.jitterSeconds = jitterSeconds;
    }

    public Duration getMutexTtl() {
        return mutexTtl;
    }

    public void setMutexTtl(Duration mutexTtl) {
        this.mutexTtl = mutexTtl;
    }

    public int getLockRetryTimes() {
        return lockRetryTimes;
    }

    public void setLockRetryTimes(int lockRetryTimes) {
        this.lockRetryTimes = lockRetryTimes;
    }

    public long getLockWaitMillis() {
        return lockWaitMillis;
    }

    public void setLockWaitMillis(long lockWaitMillis) {
        this.lockWaitMillis = lockWaitMillis;
    }
}
