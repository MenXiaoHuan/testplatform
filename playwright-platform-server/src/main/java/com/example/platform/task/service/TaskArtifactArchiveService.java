package com.example.platform.task.service;

import com.example.platform.task.model.ArtifactEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TaskArtifactArchiveService {
    List<ArtifactEntity> archiveArtifacts(
            Long taskId,
            Path workspace,
            List<String> artifactRelativeRoots,
            Map<String, ArtifactBindingTarget> bindingTargets);

    record ArtifactBindingTarget(Long caseResultId, String artifactType) {
    }
}
