package com.example.platform.scene.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SchedulerInstanceIdProvider {
    private final String instanceId;

    public SchedulerInstanceIdProvider(@Value("${platform.scheduler.instance-id:}") String configuredInstanceId) {
        String normalized = configuredInstanceId == null ? "" : configuredInstanceId.trim();
        this.instanceId = normalized.isEmpty() ? UUID.randomUUID().toString() : normalized;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
