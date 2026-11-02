package com.example.platform.patrol.dto;

/** 巡页场景创建/更新请求；认证凭据为空时保留原值。 */
public record PatrolSceneRequest(
        String name,
        String description,
        String startUrl,
        String browser,
        Boolean headless,
        String authType,
        String authCookie,
        String authToken,
        String authTokenHeader,
        String authLocalStorage,
        Long planModelId,
        Long judgeModelId,
        String taskBrief,
        Integer budgetDurationMinutes,
        Integer budgetLlmTokens,
        Integer visibleTextLimit,
        Boolean scrollScan,
        Boolean recordVideo,
        Boolean autoApproveWrite,
        Boolean autoApproveDestructive,
        Boolean scheduleEnabled,
        String cronExpression
) {
}
