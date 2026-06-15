package com.example.platform.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_stage_log")
public class TaskStageLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 32)
    private String stage;

    @Column(name = "stream_type", nullable = false, length = 16)
    private String streamType;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "line_count", nullable = false)
    private Integer lineCount;

    @Column(name = "preview_text", length = 512)
    private String previewText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

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
}
