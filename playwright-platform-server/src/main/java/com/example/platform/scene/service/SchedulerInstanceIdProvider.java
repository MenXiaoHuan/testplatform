package com.example.platform.scene.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 调度器实例 ID 提供者 —— 提供当前调度实例的唯一标识，用于租约竞争。
 *
 * <p>核心职责：
 * <ul>
 *   <li>从配置文件读取 {@code platform.scheduler.instance-id}，若未配置则自动生成 UUID</li>
 *   <li>通过 {@link #getInstanceId} 获取实例 ID，供 {@link SceneScheduleLeaseServiceImpl} 使用</li>
 * </ul>
 */
@Component
public class SchedulerInstanceIdProvider {
    private final String instanceId;

    public SchedulerInstanceIdProvider(@Value("${platform.scheduler.instance-id:}") String configuredInstanceId) {
        String normalized = configuredInstanceId == null ? "" : configuredInstanceId.trim();
        this.instanceId = normalized.isEmpty() ? UUID.randomUUID().toString() : normalized;
    }

    /** 获取当前调度实例的唯一标识。 */
    public String getInstanceId() {
        return instanceId;
    }
}
