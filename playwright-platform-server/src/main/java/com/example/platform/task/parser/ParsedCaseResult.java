package com.example.platform.task.parser;

/**
 * 解析后的用例结果记录。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装从测试结果文件中解析出的单个用例结果</li>
 *   <li>包含用例的基本信息和执行状态</li>
 *   <li>用于后续持久化到数据库</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record ParsedCaseResult(
        /** 所属任务ID */
        Long taskId,
        /** 历史ID，用于追踪用例变更历史 */
        String historyId,
        /** 用例全名 */
        String fullName,
        /** 套件名称 */
        String suiteName,
        /** 故事名称 */
        String storyName,
        /** 执行状态 */
        String status,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 所属项目名称 */
        String projectName) {
}
