package com.example.platform.task.service;

import com.example.platform.task.dto.TaskTraceShareResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface TaskTraceShareService {
    TaskTraceShareResponse createTraceShare(Long spaceId, Long taskId, Long artifactId);

    ResponseEntity<Resource> downloadSharedTrace(String token);
}
