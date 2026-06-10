package com.example.platform.report.service;

import java.nio.file.Path;

public interface ReportArchiveService {
    String archiveReport(Path workspace, Long taskId, String reportRelativePath);
}
