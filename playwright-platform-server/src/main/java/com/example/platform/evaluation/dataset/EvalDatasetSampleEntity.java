package com.example.platform.evaluation.dataset;

import java.time.LocalDateTime;

/** 评测集样本实体。 */
public class EvalDatasetSampleEntity {
    private Long id;
    private Long datasetId;
    private Integer sampleIndex;
    private String inputText;
    private String goldenAnswer;
    private String goldenAnswerVersions;
    /** 样本级判定关键词（JSON 数组），关键词匹配评估器优先使用。 */
    private String keywords;
    private String referenceContext;
    private String metadata;
    private String scenarioTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long v) { this.datasetId = v; }
    public Integer getSampleIndex() { return sampleIndex; }
    public void setSampleIndex(Integer v) { this.sampleIndex = v; }
    public String getInputText() { return inputText; }
    public void setInputText(String v) { this.inputText = v; }
    public String getGoldenAnswer() { return goldenAnswer; }
    public void setGoldenAnswer(String v) { this.goldenAnswer = v; }
    public String getGoldenAnswerVersions() { return goldenAnswerVersions; }
    public void setGoldenAnswerVersions(String v) { this.goldenAnswerVersions = v; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String v) { this.keywords = v; }
    public String getReferenceContext() { return referenceContext; }
    public void setReferenceContext(String v) { this.referenceContext = v; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String v) { this.metadata = v; }
    public String getScenarioTags() { return scenarioTags; }
    public void setScenarioTags(String v) { this.scenarioTags = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
