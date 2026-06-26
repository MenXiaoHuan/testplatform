package com.example.platform.common;

import com.example.platform.task.dto.ApplicationErrorSummaryResponse;
import com.example.platform.task.model.TaskEntity;
import java.util.List;

public interface ApplicationErrorSummaryService {
    void recordError(String loggerName, String message, Throwable throwable);

    List<ApplicationErrorSummaryResponse> listRecentForTask(TaskEntity task, int limit);
}
