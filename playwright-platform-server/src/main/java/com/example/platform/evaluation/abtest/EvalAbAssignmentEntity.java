package com.example.platform.evaluation.abtest;

/** A/B 流量分配表（V11 eval_ab_assignment）—— 固定 bucket 分组。 */
public class EvalAbAssignmentEntity {
    private Long id;
    private Long experimentId;
    private Integer userHashBucket;
    private String assignedGroup;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long v) { this.experimentId = v; }
    public Integer getUserHashBucket() { return userHashBucket; }
    public void setUserHashBucket(Integer v) { this.userHashBucket = v; }
    public String getAssignedGroup() { return assignedGroup; }
    public void setAssignedGroup(String v) { this.assignedGroup = v; }
}
