package com.example.platform.patrol.dto;

import java.util.List;

/** 任务详情：用例即 Trace 单元；配置了补充入口时 runs 按 Run 展示。 */
public record PatrolTaskDetailResponse(
        PatrolTaskResponse task,
        String snapshotJson,
        String snapshotScreenshotKey,
        String modelSnapshotJson,
        List<PatrolCaseResponse> cases,
        PatrolReportResponse report,
        List<PatrolRunResponse> runs
) {
}
