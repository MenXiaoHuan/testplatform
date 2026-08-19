package com.example.platform.task.service;

import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.parser.ParsedCaseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 用例结果持久化服务实现 —— 将解析后的用例结果转换为实体并批量插入数据库。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #persist(List)} —— 遍历用例结果列表，逐个创建实体并保存，返回 historyId 与实体 ID 的映射</li>
 * </ul>
 *
 * <p>依赖：{@link CaseResultMapper}（用例结果数据访问层）。
 */
@Service
public class TaskCaseResultPersistenceServiceImpl implements TaskCaseResultPersistenceService {
    private final CaseResultMapper repository;

    public TaskCaseResultPersistenceServiceImpl(CaseResultMapper repository) {
        this.repository = repository;
    }

    /**
     * 批量持久化用例结果，返回 historyId 到实体 ID 的映射供后续工件绑定使用。
     */
    @Override
    public Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults) {
        Map<String, Long> idsByHistoryId = new LinkedHashMap<>();
        for (ParsedCaseResult parsedCaseResult : parsedCaseResults) {
            // 将解析结果映射到实体
            CaseResultEntity entity = new CaseResultEntity();
            entity.setTaskId(parsedCaseResult.taskId());
            entity.setHistoryId(parsedCaseResult.historyId());
            entity.setFullName(parsedCaseResult.fullName());
            entity.setSuiteName(parsedCaseResult.suiteName());
            entity.setStoryName(parsedCaseResult.storyName());
            entity.setStatus(parsedCaseResult.status());
            entity.setDurationMs(parsedCaseResult.durationMs());
            entity.setProjectName(parsedCaseResult.projectName());
            repository.insert(entity);
            // 记录 historyId 到实体 ID 的映射
            idsByHistoryId.put(parsedCaseResult.historyId(), entity.getId());
        }
        return idsByHistoryId;
    }
}
