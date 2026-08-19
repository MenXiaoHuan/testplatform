package com.example.platform.runner.service;

import java.security.SecureRandom;

/**
 * Docker 容器名称工厂 —— 生成唯一、合规的 Docker 容器名称。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据任务 ID 和阶段名称生成符合 Docker 命名规范的容器名</li>
 *   <li>使用随机后缀保证名称唯一性，避免并发冲突</li>
 *   <li>限制名称长度不超过 128 个字符</li>
 * </ul>
 */
public class DockerContainerNameFactory {
    // 使用 SecureRandom 生成加密安全的随机后缀
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 创建唯一的 Docker 容器名称。
     *
     * @param taskId    任务 ID
     * @param stageName 阶段名称，为 null 或空时使用默认值 "stage"
     * @return 格式如 "playwright-platform-task-{id}-{stage}-{random}" 的容器名称
     */
    public String create(Long taskId, String stageName) {
        // 规范化阶段名称：空值使用默认值，小写转换，非法字符替换为连字符
        String stage = stageName == null || stageName.isBlank()
                ? "stage"
                : stageName.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        // 生成 36 进制随机后缀（0-9a-z），保证唯一性
        String suffix = Long.toUnsignedString(RANDOM.nextLong(), 36);
        String name = "playwright-platform-task-" + taskId + "-" + stage + "-" + suffix;
        // 截断过长的名称（Docker 限制 128 字符）
        return name.length() <= 128 ? name : name.substring(0, 128);
    }
}