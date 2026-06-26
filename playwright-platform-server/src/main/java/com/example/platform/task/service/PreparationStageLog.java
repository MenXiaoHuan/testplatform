package com.example.platform.task.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class PreparationStageLog {
    private final Path logFile;
    private int lineCount;

    private PreparationStageLog(Path logFile) {
        this.logFile = logFile;
    }

    static PreparationStageLog create() {
        try {
            return new PreparationStageLog(Files.createTempFile("task-preparing-", ".log"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create preparing stage log file", exception);
        }
    }

    void write(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String line : message.replace("\r\n", "\n").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            lines.add(Instant.now() + " " + line);
        }
        if (lines.isEmpty()) {
            return;
        }
        try {
            Files.write(
                    logFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            lineCount += lines.size();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write preparing stage log", exception);
        }
    }

    Path logFile() {
        return logFile;
    }

    int lineCount() {
        return lineCount;
    }
}
