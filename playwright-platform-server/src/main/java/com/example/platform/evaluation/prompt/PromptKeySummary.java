package com.example.platform.evaluation.prompt;

import java.time.LocalDateTime;

/** Prompt 聚合摘要 —— 按 promptKey 分组的版本数量与当前 ACTIVE 版本。 */
public class PromptKeySummary {
    private String promptKey;
    private String name;
    private String description;
    private Long versionCount;
    private String activeVersion;
    private LocalDateTime updatedAt;

    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String v) { this.promptKey = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Long getVersionCount() { return versionCount; }
    public void setVersionCount(Long v) { this.versionCount = v; }
    public String getActiveVersion() { return activeVersion; }
    public void setActiveVersion(String v) { this.activeVersion = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
