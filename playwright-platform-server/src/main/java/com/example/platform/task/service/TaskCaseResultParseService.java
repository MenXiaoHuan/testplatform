package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedTaskResults;
import java.nio.file.Path;

/**
 * 用例结果解析服务接口 —— 定义从 Playwright 测试执行结果文件中
 * 解析用例结果和工件绑定信息的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #parse(Long, Path, Path)} —— 解析 Playwright results.json 文件，提取用例结果与工件绑定关系</li>
 * </ul>
 */
public interface TaskCaseResultParseService {

    /**
     * 解析 Playwright 测试结果。
     *
     * @param taskId 任务 ID
     * @param resultsIndexFile Playwright results.json 文件路径
     * @param workspaceRoot 工作目录根路径
     * @return 解析后的任务结果（包含用例结果列表和工件绑定列表）
     */
    ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot);
}
