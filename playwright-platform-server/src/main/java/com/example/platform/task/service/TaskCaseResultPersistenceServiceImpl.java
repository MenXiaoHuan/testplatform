package com.example.platform.task.service;

import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.mapper.CaseResultMapper;
import com.example.platform.task.parser.ParsedCaseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TaskCaseResultPersistenceServiceImpl implements TaskCaseResultPersistenceService {
    private final CaseResultMapper repository;

    public TaskCaseResultPersistenceServiceImpl(CaseResultMapper repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults) {
        Map<String, Long> idsByHistoryId = new LinkedHashMap<>();
        for (ParsedCaseResult parsedCaseResult : parsedCaseResults) {
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
            idsByHistoryId.put(parsedCaseResult.historyId(), entity.getId());
        }
        return idsByHistoryId;
    }
}
