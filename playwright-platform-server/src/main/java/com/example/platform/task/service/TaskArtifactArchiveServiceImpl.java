package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.mapper.ArtifactMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 任务工件归档服务实现 —— 实现工件文件的扫描、上传与持久化逻辑。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #archiveArtifacts(Long, Path, List, Map)} —— 遍历指定目录下的所有文件，逐个上传到对象存储并保存记录</li>
 *   <li>{@link #resolveWorkspaceSubPath(Path, String, String)} —— 安全解析工作目录的子路径，防止路径穿越</li>
 *   <li>{@link #persistArtifact(Long, Path, Path, Map)} —— 上传单个工件文件并创建数据库实体</li>
 * </ul>
 *
 * <p>依赖：{@link ObjectStorageService}（对象存储服务）、{@link ArtifactMapper}（工件数据访问层）。
 */
@Service
public class TaskArtifactArchiveServiceImpl implements TaskArtifactArchiveService {
    private final ObjectStorageService objectStorageService;
    private final ArtifactMapper artifactRepository;
    private final String storageBucket;

    public TaskArtifactArchiveServiceImpl(
            ObjectStorageService objectStorageService,
            ArtifactMapper artifactRepository,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.objectStorageService = objectStorageService;
        this.artifactRepository = artifactRepository;
        this.storageBucket = storageBucket;
    }

    /**
     * 归档工件：遍历所有配置的工件根目录，上传文件到对象存储并持久化记录。
     */
    @Override
    public List<ArtifactEntity> archiveArtifacts(
            Long taskId,
            Path workspace,
            List<String> artifactRelativeRoots,
            Map<String, ArtifactBindingTarget> bindingTargets) {
        List<ArtifactEntity> archivedArtifacts = new ArrayList<>();
        for (String artifactRelativeRoot : artifactRelativeRoots) {
            // 安全解析工件根路径，防止路径穿越攻击
            Path root = resolveWorkspaceSubPath(workspace, artifactRelativeRoot, "Artifact relative path");
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .map(path -> persistArtifact(taskId, workspace, path, bindingTargets))
                        .forEach(archivedArtifacts::add);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to scan artifacts under " + artifactRelativeRoot, exception);
            }
        }
        return archivedArtifacts;
    }

    /**
     * 安全解析工作目录的子路径，确保解析后的路径仍在工作目录内，防止路径穿越。
     */
    private Path resolveWorkspaceSubPath(Path workspace, String relativePath, String label) {
        if (relativePath == null || relativePath.isBlank()) {
            return workspace.normalize();
        }
        Path normalizedWorkspace = workspace.normalize();
        Path resolved = normalizedWorkspace.resolve(relativePath).normalize();
        // 验证解析后的路径是否仍在工作目录内
        if (!resolved.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException(label + " escapes execution directory: " + relativePath);
        }
        return resolved;
    }

    /**
     * 将单个工件文件上传到对象存储，并创建对应的数据库实体记录。
     */
    private ArtifactEntity persistArtifact(
            Long taskId,
            Path workspace,
            Path file,
            Map<String, ArtifactBindingTarget> bindingTargets) {
        // 计算相对路径，用于匹配绑定目标
        String relativePath = workspace.relativize(file).toString().replace('\\', '/');
        ArtifactBindingTarget bindingTarget = bindingTargets.get(relativePath);
        // 构建对象存储键路径
        String objectKey = buildObjectKey(taskId, relativePath, bindingTarget);
        // 上传文件到对象存储
        String url = objectStorageService.uploadFile(storageBucket, objectKey, file);

        // 构建工件实体并保存
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setTaskId(taskId);
        artifact.setCaseResultId(bindingTarget == null ? null : bindingTarget.caseResultId());
        artifact.setArtifactType(bindingTarget == null ? "REPORT_FILE" : bindingTarget.artifactType());
        artifact.setBucket(storageBucket);
        artifact.setObjectKey(objectKey);
        artifact.setContentType(probeContentType(file));
        artifact.setSize(readFileSize(file));
        artifact.setUrl(url);
        artifactRepository.insert(artifact);
        return artifact;
    }

    /**
     * 构建对象存储键路径，绑定到用例结果的工件会归档到对应用例 ID 目录下。
     */
    private String buildObjectKey(Long taskId, String relativePath, ArtifactBindingTarget bindingTarget) {
        if (bindingTarget == null || bindingTarget.caseResultId() == null) {
            return "runs/" + taskId + "/artifacts/unassigned/" + relativePath;
        }
        return "runs/" + taskId + "/artifacts/" + bindingTarget.caseResultId() + "/" + relativePath;
    }

    /**
     * 探测文件的内容类型。
     */
    private String probeContentType(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * 读取文件大小。
     */
    private Long readFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact size", exception);
        }
    }
}
