package com.example.platform.task.service;

import com.example.platform.task.dto.TaskTraceShareResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

/**
 * 任务 Trace 分享服务接口 —— 定义生成带签名的临时分享链接和验证后下载 Trace 文件的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #createTraceShare(Long, Long, Long)} —— 创建 Trace 分享，生成带过期时间的签名令牌</li>
 *   <li>{@link #downloadSharedTrace(String)} —— 验证令牌并返回 Trace 文件的下载响应</li>
 * </ul>
 */
public interface TaskTraceShareService {

    /**
     * 创建 Trace 分享链接。
     *
     * @param spaceId 空间 ID
     * @param taskId 任务 ID
     * @param artifactId 工件 ID（必须是 TRACE 类型）
     * @return 包含分享 URL 和过期时间的响应
     */
    TaskTraceShareResponse createTraceShare(Long spaceId, Long taskId, Long artifactId);

    /**
     * 通过签名令牌下载分享的 Trace 文件。
     *
     * @param token 签名令牌（包含 taskId、artifactId 和过期时间）
     * @return 文件下载响应实体
     */
    ResponseEntity<Resource> downloadSharedTrace(String token);
}
