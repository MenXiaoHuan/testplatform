package com.example.platform.evaluation.prompt;

import java.time.LocalDateTime;

/** Prompt 版本实体 —— 对应 eval_prompt_version 表。 */
public class EvalPromptVersionEntity {
    private Long id;
    private Long spaceId;
    private String promptKey;
    private String name;
    private String description;
    private String promptType;
    /** JSON —— 多轮 Message 数组：[{"role":"system","content":"..."}] */
    private String template;
    private String variables;
    private String versionLabel;
    private String changeLog;
    /** DRAFT / ACTIVE / DEPRECATED */
    private String status;
    private Long supersededBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long v) { this.spaceId = v; }
    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String v) { this.promptKey = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getPromptType() { return promptType; }
    public void setPromptType(String v) { this.promptType = v; }
    public String getTemplate() { return template; }
    public void setTemplate(String v) { this.template = v; }
    public String getVariables() { return variables; }
    public void setVariables(String v) { this.variables = v; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String v) { this.versionLabel = v; }
    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String v) { this.changeLog = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getSupersededBy() { return supersededBy; }
    public void setSupersededBy(Long v) { this.supersededBy = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
