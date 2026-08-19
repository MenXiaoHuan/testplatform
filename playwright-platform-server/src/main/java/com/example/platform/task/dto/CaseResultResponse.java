package com.example.platform.task.dto;

import com.example.platform.task.model.CaseResultEntity;
import java.util.List;

/**
 * 用例结果响应 DTO。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装单个测试用例的执行结果详情</li>
 *   <li>包含用例状态、耗时、错误信息、视频/追踪/截图等制品链接</li>
 *   <li>提供静态工厂方法从实体转换为响应对象</li>
 * </ul>
 *
 * <p>依赖：{@link CaseResultEntity}、{@link CaseArtifactLinkResponse}
 */
public record CaseResultResponse(
        /** 用例结果ID */
        Long id,
        /** 所属任务ID */
        Long taskId,
        /** 历史ID，用于追踪用例变更历史 */
        String historyId,
        /** 用例全名（包含套件和故事层级） */
        String fullName,
        /** 套件名称 */
        String suiteName,
        /** 故事名称 */
        String storyName,
        /** 执行状态：PASSED、FAILED、SKIPPED 等 */
        String status,
        /** 执行耗时（毫秒） */
        Long durationMs,
        /** 所属项目名称 */
        String projectName,
        /** 错误消息 */
        String errorMessage,
        /** 视频URL */
        String videoUrl,
        /** 追踪报告URL */
        String traceUrl,
        /** 截图URL列表 */
        List<String> screenshotUrls,
        /** 日志URL */
        String logUrl,
        /** 制品数量 */
        int artifactCount,
        /** 制品链接列表 */
        List<CaseArtifactLinkResponse> artifacts) {

    /**
     * 从实体创建响应（使用默认空制品列表）
     */
    public static CaseResultResponse from(CaseResultEntity entity, int artifactCount) {
        return from(entity, null, null, null, List.of(), null, artifactCount, List.of());
    }

    /**
     * 从实体创建完整响应
     *
     * @param entity 用例结果实体
     * @param errorMessage 错误消息
     * @param videoUrl 视频URL
     * @param traceUrl 追踪URL
     * @param screenshotUrls 截图URL列表
     * @param logUrl 日志URL
     * @param artifactCount 制品数量
     * @param artifacts 制品链接列表
     */
    public static CaseResultResponse from(
            CaseResultEntity entity,
            String errorMessage,
            String videoUrl,
            String traceUrl,
            List<String> screenshotUrls,
            String logUrl,
            int artifactCount,
            List<CaseArtifactLinkResponse> artifacts) {
        return new CaseResultResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getHistoryId(),
                entity.getFullName(),
                entity.getSuiteName(),
                entity.getStoryName(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getProjectName(),
                errorMessage,
                videoUrl,
                traceUrl,
                screenshotUrls,
                logUrl,
                artifactCount,
                artifacts);
    }
}
