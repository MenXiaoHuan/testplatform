package com.example.platform.task.parser;

/**
 * 解析后的制品绑定记录。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装测试结果解析后，制品与用例结果的绑定关系</li>
 *   <li>用于在制品归档时确定制品归属的用例</li>
 * </ul>
 *
 * <p>依赖：无外部依赖
 */
public record ParsedArtifactBinding(
        /** 制品相对路径 */
        String relativePath,
        /** 制品类型：VIDEO、TRACE、SCREENSHOT、LOG 等 */
        String artifactType,
        /** 用例历史ID，用于关联到具体的用例结果 */
        String caseHistoryId) {
}
