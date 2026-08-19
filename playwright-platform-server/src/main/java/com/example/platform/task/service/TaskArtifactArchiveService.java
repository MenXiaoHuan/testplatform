package com.example.platform.task.service;

import com.example.platform.task.model.ArtifactEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 任务工件归档服务接口 —— 定义将任务执行产生的工件（日志、截图、trace 等）
 * 上传到对象存储并持久化到数据库的能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #archiveArtifacts(Long, Path, List, Map)} —— 扫描工作目录下的工件文件，上传到存储并创建数据库记录</li>
 * </ul>
 *
 * <p>依赖：{@link ArtifactBindingTarget} —— 描述工件与用例结果的绑定关系。
 */
public interface TaskArtifactArchiveService {

    /**
     * 归档工件。
     *
     * @param taskId 任务 ID
     * @param workspace 工作目录路径
     * @param artifactRelativeRoots 工件相对路径根目录列表
     * @param bindingTargets 工件路径到绑定目标的映射
     * @return 已归档的工件实体列表
     */
    List<ArtifactEntity> archiveArtifacts(
            Long taskId,
            Path workspace,
            List<String> artifactRelativeRoots,
            Map<String, ArtifactBindingTarget> bindingTargets);

    /**
     * 工件绑定目标 —— 将工件与特定用例结果关联，并标记工件类型。
     */
    record ArtifactBindingTarget(Long caseResultId, String artifactType) {
    }
}
