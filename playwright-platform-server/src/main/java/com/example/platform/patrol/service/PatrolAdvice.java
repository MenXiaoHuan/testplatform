package com.example.platform.patrol.service;

import com.example.platform.patrol.model.AiModelConfigEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型/预算失败翻译成“可执行的解决建议”，供任务概览直接展示。
 *
 * <p>建议面向使用者：先给当前配置值与建议值，其次给替代方案，最后才是排查方向。
 */
public final class PatrolAdvice {
    /** 模型配置支持的输出档位。 */
    private static final int[] TIERS = {8192, 16384, 32768, 65536};
    private static final Pattern MAX_TOKENS_PATTERN = Pattern.compile("maxTokens=(\\d+)");

    private PatrolAdvice() {
    }

    /** 模型调用失败对应的建议（去重后由调用方写入预算/任务）。 */
    public static List<String> forModelFailure(String stage, AiModelConfigEntity model, Throwable error) {
        String reason = messageOf(error);
        String lower = reason.toLowerCase(Locale.ROOT);
        String modelName = displayName(model);
        int current = currentLimit(model, reason);
        boolean timeout = hasCause(error, TimeoutException.class)
                || lower.contains("timeout") || lower.contains("timed out");
        boolean truncated = lower.contains("截断") || lower.contains("truncated")
                || lower.contains("finish_reason=length");
        boolean reasoning = lower.contains("推理") || lower.contains("reasoning");

        List<String> advice = new ArrayList<>();
        if (timeout || truncated || reasoning) {
            int next = nextTier(current);
            if (next > current) {
                advice.add(stage + "失败：模型 " + modelName + " 当前最大输出为 " + tierLabel(current)
                        + "，建议提升到 " + tierLabel(next)
                        + "（巡页 → 模型配置 → " + modelName + "）；调整后重新执行本任务。");
            } else {
                advice.add(stage + "失败：模型 " + modelName + " 已是最高档 " + tierLabel(current)
                        + "，建议改用非推理的更快模型（如 deepseek-flash），"
                        + "或降低「页面文本上限」、收敛任务描述后重试。");
            }
            if (reasoning) {
                advice.add("推理模型会把输出预算消耗在思考链上：若已升到最高档仍失败，直接改用非推理模型做"
                        + stage + "。");
            }
        } else if (lower.contains("余额") || lower.contains("balance") || lower.contains("insufficient")
                || lower.contains("quota") || lower.contains("402")) {
            advice.add("模型账户余额不足：请充值或更换 API Key，并在「模型配置」里重新测试连通性。");
        } else if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("invalid api key")) {
            advice.add("API Key 无效或无权限：请更新 API Key 后，在「模型配置」里重新测试连通性。");
        } else if (lower.contains("404") || lower.contains("model not found") || lower.contains("invalid model")) {
            advice.add("模型不存在或地址错误：请核对「模型配置」中的 Base URL 与模型 ID。");
        } else if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many")) {
            advice.add("触发模型限流：请降低巡页并发、稍后重试，或更换/升级模型配额。");
        } else if (lower.contains("connect") || lower.contains("unknown host") || lower.contains("ssl")
                || lower.contains("refused") || lower.contains("网络")) {
            advice.add("网络不可达：请检查服务端出网、Base URL 与目标模型服务是否可访问。");
        }
        if (advice.isEmpty()) {
            advice.add(stage + "失败：请查看「模型日志」中的原始错误；修复后重新执行，持续失败可更换模型。");
        }
        return advice;
    }

    /** 时长/环境类失败的建议。 */
    public static String forDuration(String what) {
        return what + "：请调大巡页「时长预算」，或收敛任务描述、减少用例数后重试。";
    }

    /** 未配置模型时的建议。 */
    public static String forMissingModel(String stage) {
        return "未配置" + stage + "模型：请在巡页中为该阶段选择可用模型后重新执行。";
    }

    private static int nextTier(int current) {
        if (current <= 0) {
            return 32768;
        }
        for (int tier : TIERS) {
            if (tier > current) {
                return tier;
            }
        }
        return current;
    }

    private static String tierLabel(int value) {
        if (value >= 65536) {
            return value + "（64k）";
        }
        if (value >= 32768) {
            return value + "（32k）";
        }
        if (value >= 16384) {
            return value + "（16k）";
        }
        if (value >= 8192) {
            return value + "（8k）";
        }
        return value <= 0 ? "未配置" : String.valueOf(value);
    }

    private static int currentLimit(AiModelConfigEntity model, String reason) {
        if (model != null && model.getMaxTokens() != null && model.getMaxTokens() > 0) {
            return model.getMaxTokens();
        }
        Matcher matcher = MAX_TOKENS_PATTERN.matcher(reason);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String displayName(AiModelConfigEntity model) {
        if (model == null) {
            return "未配置模型";
        }
        if (model.getName() != null && !model.getName().isBlank()) {
            return model.getName();
        }
        return model.getModelId() == null ? "模型" : model.getModelId();
    }

    private static String messageOf(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
