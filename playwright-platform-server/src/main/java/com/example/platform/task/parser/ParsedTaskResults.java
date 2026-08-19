package com.example.platform.task.parser;

import java.util.List;

/**
 * 解析后的任务结果集合。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装测试结果文件解析后的完整数据</li>
 *   <li>包含用例结果列表和制品绑定关系列表</li>
 *   <li>作为持久化服务的输入数据</li>
 * </ul>
 *
 * <p>依赖：{@link ParsedCaseResult}、{@link ParsedArtifactBinding}
 */
public record ParsedTaskResults(
        /** 解析出的用例结果列表 */
        List<ParsedCaseResult> caseResults,
        /** 解析出的制品绑定关系列表 */
        List<ParsedArtifactBinding> artifactBindings) {
}
