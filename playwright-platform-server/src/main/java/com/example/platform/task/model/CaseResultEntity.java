package com.example.platform.task.model;


/**
 * 用例结果实体类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>映射 case_result 数据库表</li>
 *   <li>存储单个测试用例的执行结果信息</li>
 *   <li>包含用例的层级结构（套件、故事）和执行状态</li>
 * </ul>
 *
 * <p>依赖：无外部依赖，纯 POJO
 */
public class CaseResultEntity {
    private static final int MAX_FULL_NAME_LENGTH = 512;
    private static final int MAX_NAME_LENGTH = 256;

    /** 用例结果ID */
    private Long id;

    /** 所属任务ID */
    private Long taskId;

    /** 历史ID，用于追踪用例变更历史 */
    private String historyId;

    /** 用例全名（包含层级路径） */
    private String fullName;

    /** 套件名称 */
    private String suiteName;

    /** 故事名称 */
    private String storyName;

    /** 执行状态：PASSED、FAILED、SKIPPED 等 */
    private String status;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 用例负责人 */
    private String ownerName;

    /** 严重等级 */
    private String severity;

    /** 所属项目名称 */
    private String projectName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = truncate(historyId, MAX_NAME_LENGTH); }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = truncate(fullName, MAX_FULL_NAME_LENGTH); }
    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = truncate(suiteName, MAX_NAME_LENGTH); }
    public String getStoryName() { return storyName; }
    public void setStoryName(String storyName) { this.storyName = truncate(storyName, MAX_NAME_LENGTH); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = truncate(ownerName, 128); }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = truncate(severity, 64); }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = truncate(projectName, 64); }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
