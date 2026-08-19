package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import org.springframework.stereotype.Service;

/**
 * 任务命令构建器实现 —— 根据代码仓库配置和场景配置构建完整的 Playwright 测试执行命令。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #buildRunCommand(TestRepositoryEntity, SceneEntity)} —— 确定基础命令 → 拼接过滤目标 → 添加浏览器项目参数</li>
 * </ul>
 */
@Service
public class TaskCommandBuilderImpl implements TaskCommandBuilder {

    /**
     * 构建测试执行命令：优先使用仓库配置的命令模板，回退到场景配置的命令。
     * 根据场景的匹配值拼接过滤目标，根据浏览器配置添加项目参数。
     */
    @Override
    public String buildRunCommand(TestRepositoryEntity repository, SceneEntity scene) {
        // 优先使用仓库的命令模板，回退到场景的运行命令
        String baseCommand = repository.getRunCommandTemplate();
        if (baseCommand == null || baseCommand.isBlank()) {
            baseCommand = scene.getRunCommand();
        }
        if (baseCommand == null) {
            baseCommand = "";
        }

        StringBuilder builder = new StringBuilder(baseCommand);
        boolean isPlaywrightNpxCommand = baseCommand.trim().startsWith("npx playwright test");
        String resolvedTarget = null;

        // 处理场景匹配值，拼接完整的测试目标路径
        String matchValue = scene.getMatchValue();
        if (matchValue != null && !matchValue.isBlank()) {
            String normalizedRoot = repository.getTestRoot() == null
                    ? ""
                    : repository.getTestRoot().replaceAll("/+$", "");
            String normalizedMatch = matchValue.replaceAll("^/+", "");
            resolvedTarget = normalizedRoot.isBlank()
                    ? normalizedMatch
                    : normalizedRoot + "/" + normalizedMatch;

            // 根据命令类型选择不同的目标参数格式
            if (isPlaywrightNpxCommand) {
                builder.append(" ").append(resolvedTarget);
            } else {
                builder.append(" --target ").append(resolvedTarget);
            }
        }

        // 添加浏览器项目参数
        if (scene.getBrowser() != null && !scene.getBrowser().isBlank()) {
            builder.append(" --project ").append(scene.getBrowser().trim());
        }

        return builder.toString();
    }
}
