package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;

/**
 * 任务命令构建器接口 —— 定义根据代码仓库配置和场景配置构建测试执行命令的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #buildRunCommand(TestRepositoryEntity, SceneEntity)} —— 结合仓库的命令模板和场景的过滤条件、浏览器配置生成完整命令</li>
 * </ul>
 */
public interface TaskCommandBuilder {

    /**
     * 构建测试执行命令。
     *
     * @param repository 代码仓库配置（包含命令模板、测试根目录等）
     * @param scene 场景配置（包含匹配值、浏览器类型等）
     * @return 完整的执行命令字符串
     */
    String buildRunCommand(TestRepositoryEntity repository, SceneEntity scene);
}
