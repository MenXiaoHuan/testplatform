package com.example.platform.patrol.model;

import java.util.Set;

/**
 * 巡页任务状态常量。
 *
 * <p>注意：{@code status} 与 {@code current_stage} 是两套值，禁止混用。
 * status = QUEUED / RUNNING / WAITING_APPROVAL / COMPLETED / FAILED / CANCELED / UNREACHABLE；
 * current_stage = PREPARING / SNAPSHOTTING / PLANNING / WAITING_APPROVAL / WAITING_RUNS / EXECUTING / JUDGING / FINISHED。
 */
public final class PatrolTaskStatus {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";
    /** 补充入口的起始状态不可达（前置无法解析或执行失败）；不算任务失败。 */
    public static final String UNREACHABLE = "UNREACHABLE";

    /** 占用场景互斥与平台并发名额的活动状态。 */
    public static final Set<String> ACTIVE = Set.of(QUEUED, RUNNING, WAITING_APPROVAL);
    /** 允许用户取消的状态。 */
    public static final Set<String> CANCELABLE = Set.of(QUEUED, RUNNING, WAITING_APPROVAL);
    /** 已结束、不再占用的状态。 */
    public static final Set<String> FINISHED = Set.of(COMPLETED, FAILED, CANCELED, UNREACHABLE);

    private PatrolTaskStatus() {
    }
}
