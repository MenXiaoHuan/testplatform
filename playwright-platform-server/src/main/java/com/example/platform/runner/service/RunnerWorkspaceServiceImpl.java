package com.example.platform.runner.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 运行器工作空间服务实现 —— 基于 Git 克隆的工作空间管理。
 *
 * <p>核心职责：
 * <ul>
 *   <li>使用 git clone --depth=1 浅克隆代码仓库到任务专属目录</li>
 *   <li>在准备前清理可能存在的旧工作空间</li>
 *   <li>支持任务完成后的工作空间清理，释放磁盘空间</li>
 * </ul>
 *
 * <p>依赖：platform.runner.workspace-root 配置项
 */
@Service
public class RunnerWorkspaceServiceImpl implements RunnerWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(RunnerWorkspaceServiceImpl.class);
    private final Path workspaceRoot;

    public RunnerWorkspaceServiceImpl(@Value("${platform.runner.workspace-root}") String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot);
    }

    /**
     * 准备任务工作空间，执行 Git 浅克隆。
     */
    @Override
    public Path prepareWorkspace(String gitUrl, String branch, Long taskId) {
        try {
            // 确保工作空间根目录存在
            Files.createDirectories(workspaceRoot);
            Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
            // 清理可能残留的旧工作空间
            deleteWorkspace(taskWorkspace);
            // 执行 Git 浅克隆，指定分支
            Process process = new ProcessBuilder("git", "clone", "--depth", "1", "--branch", branch, gitUrl, taskWorkspace.toString())
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Git clone failed with exit code " + exitCode);
            }
            return taskWorkspace;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare workspace", exception);
        }
    }

    /**
     * 清理任务工作空间，删除任务目录及其所有内容。
     */
    @Override
    public void cleanupWorkspace(Long taskId) {
        if (taskId == null) {
            return;
        }
        Path taskWorkspace = workspaceRoot.resolve(String.valueOf(taskId));
        try {
            deleteWorkspace(taskWorkspace);
        } catch (IOException exception) {
            log.warn("Failed to cleanup runner workspace. taskId={}, reason={}", taskId, exception.getMessage());
        }
    }

    /**
     * 递归删除工作空间目录及其所有内容。
     * 使用倒序遍历确保先删除文件再删除目录。
     *
     * @param taskWorkspace 要删除的目录路径
     */
    private void deleteWorkspace(Path taskWorkspace) throws IOException {
        if (!Files.exists(taskWorkspace)) {
            return;
        }
        // 倒序遍历目录树，确保子文件先于父目录删除
        try (var walk = Files.walk(taskWorkspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}