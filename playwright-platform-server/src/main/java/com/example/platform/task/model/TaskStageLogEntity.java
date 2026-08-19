package com.example.platform.task.model;

import java.time.LocalDateTime;

/**
 * 任务阶段日志实体类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>映射 task_stage_log 数据库表</li>
 *   <li>存储任务执行各阶段的日志信息</li>
 *   <li>包含日志文件的存储位置、执行结果、预览文本等</li>
 * </ul>
 *
 * <p>依赖：{@link LocalDateTime}
 */
public class TaskStageLogEntity {
    /** 阶段日志ID */
    private Long id;

    /** 所属任务ID */
    private Long taskId;

    /** 阶段名称：PREPARING、INSTALLING、TESTING、ARCHIVING 等 */
    private String stage;

    /** 流类型：stdout、stderr、combined */
    private String streamType;

    /** 对象键（存储路径） */
    private String objectKey;

    /** 内容类型（MIME类型） */
    private String contentType;

    /** 文件大小（字节） */
    private Long size;

    /** 日志行数 */
    private Integer lineCount;

    /** 预览文本（日志前N行） */
    private String previewText;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 退出码 */
    private Integer exitCode;

    /** 阶段状态 */
    private String stageStatus;

    /** 执行命令 */
    private String command;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime endedAt;

    /** 错误消息 */
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStreamType() { return streamType; }
    public void setStreamType(String streamType) { this.streamType = streamType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Integer getLineCount() { return lineCount; }
    public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getStageStatus() { return stageStatus; }
    public void setStageStatus(String stageStatus) { this.stageStatus = stageStatus; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
