package com.example.platform.common;

import com.example.platform.task.dto.ApplicationErrorSummaryResponse;
import com.example.platform.task.model.TaskEntity;
import java.util.List;

/**
 * 应用错误摘要服务 —— 记录并查询运行时错误摘要。
 *
 * <p>核心职责：
 * <ul>
 *   <li>在 {@link GlobalExceptionHandler} 捕获到未处理异常时调用 {@link #recordError} 进行记录</li>
 *   <li>提供 {@link #listRecentForTask} 供任务详情页查询最近的错误摘要</li>
 * </ul>
 *
 * <p>实现类：
 * <ul>
 *   <li>{@link InMemoryApplicationErrorSummaryService}：默认的内存环形缓冲实现</li>
 * </ul>
 */
public interface ApplicationErrorSummaryService {

    /**
     * 记录一次异常摘要。
     *
     * @param loggerName  触发异常的 logger 名称
     * @param message     异常消息
     * @param throwable   异常对象（可能为 null）
     */
    void recordError(String loggerName, String message, Throwable throwable);

    /**
     * 查询指定任务最近的错误摘要，按时间倒序返回最多 {@code limit} 条。
     *
     * @param task  所属任务（可能为 null）
     * @param limit 最大返回条数
     * @return 错误摘要列表
     */
    List<ApplicationErrorSummaryResponse> listRecentForTask(TaskEntity task, int limit);
}
