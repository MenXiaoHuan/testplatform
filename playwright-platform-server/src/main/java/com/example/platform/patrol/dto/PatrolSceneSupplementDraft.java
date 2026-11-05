package com.example.platform.patrol.dto;

import java.util.List;

/** 场景补充（补充入口 Run）配置。 */
public record PatrolSceneSupplementDraft(
        Long id,
        String name,
        String entryType,
        String preconditionText,
        String startUrl,
        String focusText,
        Boolean enabled,
        List<ResolvedStep> steps,
        Double confidence,
        String unresolvedReason
) {
    /** 前置解析出的单个步骤。 */
    public record ResolvedStep(
            String action,
            String selector,
            List<String> frame,
            String target,
            String value,
            Double confidence,
            String reason
    ) {
    }
}
