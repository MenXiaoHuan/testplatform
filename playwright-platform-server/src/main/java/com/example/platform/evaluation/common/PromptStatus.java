package com.example.platform.evaluation.common;

/** Prompt 版本状态枚举，对应 eval_prompt_version.status。 */
public enum PromptStatus {
    /** 草稿，可编辑，不对外提供拉取 */
    DRAFT,
    /** 生效中，同 promptKey + spaceId 唯一 */
    ACTIVE,
    /** 被新版本废弃，不自动拉取 */
    DEPRECATED
}
