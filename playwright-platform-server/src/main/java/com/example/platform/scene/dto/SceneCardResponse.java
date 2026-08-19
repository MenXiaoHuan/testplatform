package com.example.platform.scene.dto;

import java.time.LocalDateTime;

/**
 * 场景卡片响应 DTO —— 用于前端场景列表页的精简展示。
 *
 * @param id                       场景 ID
 * @param repoId                   关联仓库 ID
 * @param name                     场景名称
 * @param description              场景描述
 * @param branch                   Git 分支
 * @param scheduleEnabled          是否启用定时调度
 * @param cronExpression           cron 表达式
 * @param lastTaskStatus           最近一次任务状态
 * @param lastRunAt                最近一次运行时间
 * @param environmentVariableCount 环境变量数量
 */
public record SceneCardResponse(
        Long id,
        Long repoId,
        String name,
        String description,
        String branch,
        boolean scheduleEnabled,
        String cronExpression,
        String lastTaskStatus,
        LocalDateTime lastRunAt,
        int environmentVariableCount) {
}
