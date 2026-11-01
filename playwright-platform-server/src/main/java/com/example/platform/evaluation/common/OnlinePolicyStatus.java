package com.example.platform.evaluation.common;

/** 在线评测策略状态枚举。 */
public enum OnlinePolicyStatus {
    /** 启用中，策略引擎周期扫描 */
    ACTIVE,
    /** 暂停（手动 / Langfuse 不可用时自动） */
    PAUSED
}
