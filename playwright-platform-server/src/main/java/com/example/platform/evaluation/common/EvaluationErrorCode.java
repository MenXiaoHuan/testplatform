package com.example.platform.evaluation.common;

/** 评测模块统一业务错误码。可被 GlobalExceptionHandler 通过 BusinessException.code 映射为 HTTP 状态。 */
public final class EvaluationErrorCode {

    private EvaluationErrorCode() {}

    // 4xx
    public static final String BAD_REQUEST            = "BAD_REQUEST";
    public static final String TARGET_NOT_FOUND        = "TARGET_NOT_FOUND";
    public static final String PROMPT_NOT_FOUND        = "PROMPT_NOT_FOUND";
    public static final String DATASET_NOT_FOUND       = "DATASET_NOT_FOUND";
    public static final String TARGET_INVOKE_FAILED    = "TARGET_INVOKE_FAILED";
    public static final String SSVR_VIOLATION          = "SSRF_VIOLATION";
    public static final String PROMPT_VERSION_CONFLICT = "PROMPT_VERSION_CONFLICT";

    // 409 Conflict 族 —— 并发/护栏
    public static final String SPACE_GUARDIAN_HIT     = "SPACE_GUARDIAN_HIT";
    public static final String TARGET_GUARDIAN_HIT    = "TARGET_GUARDIAN_HIT";
    public static final String RUN_DEDUPLICATED        = "RUN_DEDUPLICATED";
    public static final String RUN_VERSION_CONFLICT    = "RUN_VERSION_CONFLICT";

    // 5xx
    public static final String LANGFUSE_UNAVAILABLE    = "LANGFUSE_UNAVAILABLE";
    public static final String EVALUATION_INTERNAL     = "EVALUATION_INTERNAL";
}
