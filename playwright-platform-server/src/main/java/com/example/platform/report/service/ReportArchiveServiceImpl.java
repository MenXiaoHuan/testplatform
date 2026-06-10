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
        Path reportDir = workspace.resolve(reportRelativePath);
        if (!Files.exists(reportDir)) {
            return null;
        }
        return objectStorageService.uploadDirectory(bucket, "runs/" + taskId + "/report", reportDir);
    }
}
