package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
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

@Service
public class TaskArtifactArchiveServiceImpl implements TaskArtifactArchiveService {
    private final ObjectStorageService objectStorageService;
    private final ArtifactJpaRepository artifactRepository;
    private final String storageBucket;

    public TaskArtifactArchiveServiceImpl(
            ObjectStorageService objectStorageService,
            ArtifactJpaRepository artifactRepository,
            @Value("${platform.storage.bucket}") String storageBucket) {
        this.objectStorageService = objectStorageService;
        this.artifactRepository = artifactRepository;
        this.storageBucket = storageBucket;
    }

    @Override
    public List<ArtifactEntity> archiveArtifacts(
            Long taskId,
            Path workspace,
            List<String> artifactRelativeRoots,
            Map<String, ArtifactBindingTarget> bindingTargets) {
        List<ArtifactEntity> archivedArtifacts = new ArrayList<>();
        for (String artifactRelativeRoot : artifactRelativeRoots) {
            Path root = workspace.resolve(artifactRelativeRoot);
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

    private ArtifactEntity persistArtifact(
            Long taskId,
            Path workspace,
            Path file,
            Map<String, ArtifactBindingTarget> bindingTargets) {
        String relativePath = workspace.relativize(file).toString().replace('\\', '/');
        ArtifactBindingTarget bindingTarget = bindingTargets.get(relativePath);
        String objectKey = buildObjectKey(taskId, relativePath, bindingTarget);
        String url = objectStorageService.uploadFile(storageBucket, objectKey, file);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setTaskId(taskId);
        artifact.setCaseResultId(bindingTarget == null ? null : bindingTarget.caseResultId());
        artifact.setArtifactType(bindingTarget == null ? "REPORT_FILE" : bindingTarget.artifactType());
        artifact.setBucket(storageBucket);
        artifact.setObjectKey(objectKey);
        artifact.setContentType(probeContentType(file));
        artifact.setSize(readFileSize(file));
        artifact.setUrl(url);
        return artifactRepository.save(artifact);
    }

    private String buildObjectKey(Long taskId, String relativePath, ArtifactBindingTarget bindingTarget) {
        if (bindingTarget == null || bindingTarget.caseResultId() == null) {
            return "runs/" + taskId + "/artifacts/unassigned/" + relativePath;
        }
        return "runs/" + taskId + "/artifacts/" + bindingTarget.caseResultId() + "/" + relativePath;
    }

    private String probeContentType(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long readFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact size", exception);
        }
    }
}
