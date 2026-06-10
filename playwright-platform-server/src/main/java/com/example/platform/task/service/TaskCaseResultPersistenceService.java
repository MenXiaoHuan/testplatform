package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedCaseResult;
import java.util.List;
import java.util.Map;

public interface TaskCaseResultPersistenceService {
    Map<String, Long> persist(List<ParsedCaseResult> parsedCaseResults);
}
