package com.example.platform.runner.service;

import java.nio.file.Path;

/**
 * 运行器工作空间服务接口 —— 管理任务工作空间的准备和清理。
 *
 * <p>核心职责：
 * <ul>
 *   <li>准备工作空间：克隆 Git 仓库到指定路径</li>
 *   <li>清理工作空间：删除任务完成后的临时文件</li>
 * </ul>
 *
 * <p>实现类：{@link RunnerWorkspaceServiceImpl}
 */
public interface RunnerWorkspaceService {
    /**
     * 准备任务工作空间，将指定 Git 仓库克隆到工作空间根目录。
     *
     * @param gitUrl   Git 仓库地址
     * @param branch   分支名称
     * @param taskId   任务 ID（用于创建独立目录）
     * @return 克隆后的工作空间路径
     */
    Path prepareWorkspace(String gitUrl, String branch, Long taskId);

    /**
     * 清理任务工作空间，删除对应的临时目录。
     *
     * @param taskId 任务 ID
     */
    void cleanupWorkspace(Long taskId);
}