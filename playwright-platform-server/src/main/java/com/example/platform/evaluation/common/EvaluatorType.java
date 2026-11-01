package com.example.platform.evaluation.common;

/** 评分器类型枚举，对应 evaluator.type 字段。 */
public enum EvaluatorType {
    /** 关键词匹配（OR / AND / 正则） */
    RULE_MATCH,
    /** 精确匹配（trim / ignoreCase 可选） */
    EXACT_MATCH,
    /** LLM-as-Judge（三层预算墙） */
    LLM_AS_JUDGE,
    /** 语义相似度（后续迭代可降级为规则） */
    SIMILARITY,
    /** JSON Schema 校验 */
    JSON_SCHEMA,
    /** 代码执行（语法检查 + 单元测试） */
    CODE_EXECUTION,
    /** Trace 断言（span 存在/数量/错误/延迟/Token）—— 离线评测读取 Langfuse 链路 */
    TRACE_ASSERTION,
    /** Trace 快照对比 —— 与 golden span 序列比较，忽略 ID/时间等易变字段 */
    TRACE_SNAPSHOT,
    /** 人工评分（手动入口） */
    MANUAL
}
