package com.example.platform.task.service;

import com.example.platform.task.parser.ParsedTaskResults;
import java.nio.file.Path;

public interface TaskCaseResultParseService {
    ParsedTaskResults parse(Long taskId, Path resultsIndexFile, Path workspaceRoot);
}
