package com.example.platform.task.model;


/**
 * 制品实体类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>映射 artifact 数据库表</li>
 *   <li>存储任务执行产生的制品信息（视频、追踪、截图、日志等）</li>
 *   <li>包含制品的存储位置信息（桶、对象键、URL）</li>
 * </ul>
 *
 * <p>依赖：无外部依赖，纯 POJO
 */
public class ArtifactEntity {
    /** 制品ID */
    private Long id;

    /** 所属任务ID */
    private Long taskId;

    /** 所属用例结果ID（可为空，表示任务级制品） */
    private Long caseResultId;

    /** 制品类型：VIDEO、TRACE、SCREENSHOT、LOG、OTHER */
    private String artifactType;

    /** 存储桶名称 */
    private String bucket;

    /** 对象键（存储路径） */
    private String objectKey;

    /** 内容类型（MIME类型） */
    private String contentType;

    /** 文件大小（字节） */
    private Long size;

    /** 访问URL */
    private String url;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getCaseResultId() { return caseResultId; }
    public void setCaseResultId(Long caseResultId) { this.caseResultId = caseResultId; }
    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
