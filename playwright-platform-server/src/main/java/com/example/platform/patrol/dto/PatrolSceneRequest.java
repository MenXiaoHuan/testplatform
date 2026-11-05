package com.example.platform.patrol.dto;

import java.util.List;

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
        Long resolveModelId,
        Long supplementModelId,
        List<PatrolSceneSupplementDraft> supplements,
        Integer budgetDurationMinutes,
        Integer visibleTextLimit,
        Boolean scrollScan,
        Boolean recordVideo,
        Boolean autoApproveWrite,
        Boolean autoApproveDestructive,
        Boolean scheduleEnabled,
        String cronExpression
) {
}
