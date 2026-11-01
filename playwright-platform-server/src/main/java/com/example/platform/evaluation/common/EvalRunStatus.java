package com.example.platform.evaluation.common;

/** 离线评测 Run 状态机枚举。 */
public enum EvalRunStatus {
    /** 已创建，等待调度 */
    PENDING,
    /** 正在执行（Adapter 调用 + 评分） */
    RUNNING,
    /** 正常完成 */
    COMPLETED,
    /** 全部或关键失败 */
    FAILED,
    /** 用户主动取消（或超时自动取消） */
    CANCELLED
}
