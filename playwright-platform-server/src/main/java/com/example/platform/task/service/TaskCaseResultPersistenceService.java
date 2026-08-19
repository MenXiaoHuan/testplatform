package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedCaseResult;
import java.util.List;
import java.util.Map;

/**
 * 用例结果持久化服务接口 —— 定义将解析后的用例结果保存到数据库的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #persist(List)} —— 批量持久化用例结果，并返回 historyId 到实体 ID 的映射</li>
 * </ul>
 */
public interface TaskCaseResultPersistenceService {

    /**
     * 批量持久化用例结果。
     *
     * @param parsedCaseResults 解析后的用例结果列表
     * @return historyId 到实体 ID 的映射，用于后续关联工件绑定
     */
    Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults);
}
