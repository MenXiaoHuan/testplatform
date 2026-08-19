package com.example.platform.runner.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Docker 命令构建器 —— 构建用于在 Docker 容器中执行任务阶段的命令。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据请求构建 docker run 命令，包含内存、CPU、网络等资源限制</li>
 *   <li>处理工作目录的路径映射（主机路径 → 容器路径）</li>
 *   <li>按字母顺序添加环境变量，保证命令确定性</li>
 * </ul>
 *
 * <p>依赖：{@link DockerRunnerProperties}、{@link RunnerProperties}
 */
public class DockerCommandBuilder {
    private final DockerRunnerProperties dockerProperties;
    private final RunnerProperties runnerProperties;

    public DockerCommandBuilder(DockerRunnerProperties dockerProperties, RunnerProperties runnerProperties) {
        this.dockerProperties = dockerProperties;
        this.runnerProperties = runnerProperties;
    }

    /**
     * 构建 docker run 命令列表。
     *
     * @param request       执行请求，包含工作目录、环境变量等信息
     * @param containerName 容器名称
     * @return docker 命令参数列表
     */
    public List<String> buildRunCommand(RunnerCommandRequest request, String containerName) {
        // 解析并规范化工作空间根目录
        Path workspaceRoot = request.workspaceRoot().toAbsolutePath().normalize();
        // 解析并规范化工作目录
        Path workingDirectory = request.workingDirectory().toAbsolutePath().normalize();
        // 校验工作目录不得超出工作空间范围
        if (!workingDirectory.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Working directory escapes runner workspace: " + request.workingDirectory());
        }

        // 将工作空间路径映射为主机路径（用于 Docker 卷挂载）
        Path hostWorkspace = resolveHostWorkspace(workspaceRoot);
        // 计算容器内的工作目录路径
        String containerWorkdir = resolveContainerWorkdir(workspaceRoot, workingDirectory);

        // 组装 docker run 命令
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        // 配置容器退出后自动删除
        if (dockerProperties.isRemoveContainer()) {
            command.add("--rm");
        }
        command.add("--name");
        command.add(containerName);
        command.add("--workdir");
        command.add(containerWorkdir);
        // 设置内存限制
        command.add("--memory");
        command.add(dockerProperties.getMemory());
        // 设置 CPU 限制
        command.add("--cpus");
        command.add(dockerProperties.getCpus());
        // 设置网络模式
        command.add("--network");
        command.add(dockerProperties.getNetwork());
        // 按 key 排序添加环境变量，保证命令确定性
        request.extraEnv().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    command.add("-e");
                    command.add(entry.getKey() + "=" + entry.getValue());
                });
        // 添加卷挂载：主机工作空间 → 容器工作目录，读写模式
        command.add("-v");
        command.add(hostWorkspace + ":" + dockerProperties.getContainerWorkspaceRoot() + ":rw");
        // 指定使用的镜像
        command.add(dockerProperties.getImage());
        // 通过 shell 执行用户命令
        command.add("/bin/sh");
        command.add("-lc");
        command.add(request.command());
        return command;
    }

    /**
     * 将工作空间路径解析为主机实际路径。
     * 当配置了 hostWorkspaceRoot 时，进行路径映射。
     *
     * @param workspaceRoot 配置的工作空间根目录
     * @return 主机上的实际路径
     */
    private Path resolveHostWorkspace(Path workspaceRoot) {
        Path configuredWorkspaceRoot = runnerProperties.getWorkspaceRoot().toAbsolutePath().normalize();
        Path configuredHostWorkspaceRoot = runnerProperties.getHostWorkspaceRoot().toAbsolutePath().normalize();
        // 若不在配置的工作空间根目录下，直接返回原路径
        if (!workspaceRoot.startsWith(configuredWorkspaceRoot)) {
            return workspaceRoot;
        }
        // 计算相对路径并拼接到主机根目录
        Path relative = configuredWorkspaceRoot.relativize(workspaceRoot);
        return configuredHostWorkspaceRoot.resolve(relative).normalize();
    }

    /**
     * 解析容器内的工作目录路径（Unix 格式）。
     *
     * @param workspaceRoot   工作空间根目录
     * @param workingDirectory 工作目录
     * @return 容器内的绝对路径
     */
    private String resolveContainerWorkdir(Path workspaceRoot, Path workingDirectory) {
        // 计算相对于工作空间根目录的路径
        Path relative = workspaceRoot.relativize(workingDirectory);
        // 转换为 Unix 路径格式
        String relativeUnix = relative.toString().replace('\\', '/');
        // 若相对路径为空，直接返回容器工作空间根目录
        if (relativeUnix.isBlank()) {
            return dockerProperties.getContainerWorkspaceRoot();
        }
        return dockerProperties.getContainerWorkspaceRoot() + "/" + relativeUnix;
    }
}