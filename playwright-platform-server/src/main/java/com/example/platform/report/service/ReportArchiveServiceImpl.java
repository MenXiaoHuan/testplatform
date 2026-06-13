package com.example.platform.report.service;

import com.example.platform.storage.service.ObjectStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportArchiveServiceImpl implements ReportArchiveService {
    private final ObjectStorageService objectStorageService;
    private final String bucket;

    public ReportArchiveServiceImpl(ObjectStorageService objectStorageService,
                                    @Value("${platform.storage.bucket}") String bucket) {
        this.objectStorageService = objectStorageService;
        this.bucket = bucket;
    }

    @Override
    public String archiveReport(Path workspace, Long taskId, String reportRelativePath) {
        Path reportDir = resolveWorkspaceSubPath(workspace, reportRelativePath, "Report relative path", true);
        if (!Files.exists(reportDir)) {
            return null;
        }
        return objectStorageService.uploadDirectory(bucket, "runs/" + taskId + "/report", reportDir);
    }

    private Path resolveWorkspaceSubPath(Path workspace, String relativePath, String label, boolean requireNonBlank) {
        if (relativePath == null || relativePath.isBlank()) {
            if (requireNonBlank) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            return workspace.normalize();
        }
        Path normalizedWorkspace = workspace.normalize();
        Path resolved = normalizedWorkspace.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException(label + " escapes execution directory: " + relativePath);
        }
        return resolved;
    }
}
