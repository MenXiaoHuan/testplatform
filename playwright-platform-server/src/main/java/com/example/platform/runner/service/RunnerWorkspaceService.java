package com.example.platform.runner.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 运行器工作空间服务接口 —— 管理任务工作空间的准备和清理。
 *
 * <p>准备阶段会按仓库配置的认证方式（公有 / HTTPS Token / SSH Key）clone 代码；
 * 凭据只在 clone 子进程内使用，不写入 gitUrl 或日志。
 */
public interface RunnerWorkspaceService {
    /**
     * 准备任务工作空间。
     *
     * @param repository  代码仓库配置（含加密凭据）
     * @param branch      分支
     * @param taskId      任务 ID
     * @param logConsumer 接收 git clone 输出的回调，用于 PREPARING 阶段日志
     * @return 克隆后的工作空间路径
     */
    Path prepareWorkspace(TestRepositoryEntity repository, String branch, Long taskId,
                          Consumer<String> logConsumer);

    /**
     * 清理任务工作空间，删除对应的临时目录。
     */
    void cleanupWorkspace(Long taskId);
}
