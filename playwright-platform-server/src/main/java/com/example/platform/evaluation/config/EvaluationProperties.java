package com.example.platform.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 评测平台全局配置（application.yml platform.evaluation.*）。 */
@ConfigurationProperties(prefix = "platform.evaluation")
public class EvaluationProperties {

    private Offline offline = new Offline();
    private Online online = new Online();
    private Judge judge = new Judge();
    private Prompt prompt = new Prompt();

    public Offline getOffline() { return offline; }
    public Online getOnline() { return online; }
    public Judge getJudge() { return judge; }
    public Prompt getPrompt() { return prompt; }

    public static class Offline {
        private int maxConcurrent = 4;
        private long runTimeoutSeconds = 7200;
        private long sampleTimeoutSeconds = 120;
        private int judgeMaxRetries = 2;
        private int maxRunningPerSpace = 3;      // C15
        private int maxTargetConcurrentRuns = 2;  // C16

        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int v) { this.maxConcurrent = v; }
        public long getRunTimeoutSeconds() { return runTimeoutSeconds; }
        public void setRunTimeoutSeconds(long v) { this.runTimeoutSeconds = v; }
        public long getSampleTimeoutSeconds() { return sampleTimeoutSeconds; }
        public void setSampleTimeoutSeconds(long v) { this.sampleTimeoutSeconds = v; }
        public int getJudgeMaxRetries() { return judgeMaxRetries; }
        public void setJudgeMaxRetries(int v) { this.judgeMaxRetries = v; }
        public int getMaxRunningPerSpace() { return maxRunningPerSpace; }
        public void setMaxRunningPerSpace(int v) { this.maxRunningPerSpace = v; }
        public int getMaxTargetConcurrentRuns() { return maxTargetConcurrentRuns; }
        public void setMaxTargetConcurrentRuns(int v) { this.maxTargetConcurrentRuns = v; }
    }

    public static class Online {
        private int langfuseGatekeeperMaxConcurrent = 8;    // C21
        private long langfuseQueryCacheTtlSeconds = 60;    // C21
        private long metricsCacheTtlSeconds = 60;
        private int policyScanBaseIntervalMinutes = 5;
        private int policyScanJitterPercent = 20;          // C17

        public int getLangfuseGatekeeperMaxConcurrent() { return langfuseGatekeeperMaxConcurrent; }
        public void setLangfuseGatekeeperMaxConcurrent(int v) { this.langfuseGatekeeperMaxConcurrent = v; }
        public long getLangfuseQueryCacheTtlSeconds() { return langfuseQueryCacheTtlSeconds; }
        public void setLangfuseQueryCacheTtlSeconds(long v) { this.langfuseQueryCacheTtlSeconds = v; }
        public long getMetricsCacheTtlSeconds() { return metricsCacheTtlSeconds; }
        public void setMetricsCacheTtlSeconds(long v) { this.metricsCacheTtlSeconds = v; }
        public int getPolicyScanBaseIntervalMinutes() { return policyScanBaseIntervalMinutes; }
        public void setPolicyScanBaseIntervalMinutes(int v) { this.policyScanBaseIntervalMinutes = v; }
        public int getPolicyScanJitterPercent() { return policyScanJitterPercent; }
        public void setPolicyScanJitterPercent(int v) { this.policyScanJitterPercent = v; }
    }

    public static class Judge {
        private long globalDailyTokens = 5_000_000L;        // C18
        private double spaceDailyTokensDefaultFraction = 0.5;
        private boolean autoDowngradeOnBudgetExhausted = true;

        public long getGlobalDailyTokens() { return globalDailyTokens; }
        public void setGlobalDailyTokens(long v) { this.globalDailyTokens = v; }
        public double getSpaceDailyTokensDefaultFraction() { return spaceDailyTokensDefaultFraction; }
        public void setSpaceDailyTokensDefaultFraction(double v) { this.spaceDailyTokensDefaultFraction = v; }
        public boolean isAutoDowngradeOnBudgetExhausted() { return autoDowngradeOnBudgetExhausted; }
        public void setAutoDowngradeOnBudgetExhausted(boolean v) { this.autoDowngradeOnBudgetExhausted = v; }
    }

    public static class Prompt {
        private int cacheMaxSize = 1000;
        private long cacheExpireAfterWriteSeconds = 300;    // C23
        private boolean seedFromClasspathAgentMd = true;   // C24

        public int getCacheMaxSize() { return cacheMaxSize; }
        public void setCacheMaxSize(int v) { this.cacheMaxSize = v; }
        public long getCacheExpireAfterWriteSeconds() { return cacheExpireAfterWriteSeconds; }
        public void setCacheExpireAfterWriteSeconds(long v) { this.cacheExpireAfterWriteSeconds = v; }
        public boolean isSeedFromClasspathAgentMd() { return seedFromClasspathAgentMd; }
        public void setSeedFromClasspathAgentMd(boolean v) { this.seedFromClasspathAgentMd = v; }
    }
}
