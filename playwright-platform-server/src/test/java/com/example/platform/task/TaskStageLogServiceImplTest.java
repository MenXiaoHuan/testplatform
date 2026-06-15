package com.example.platform.task;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.TaskStageLogEntity;
import com.example.platform.task.model.TaskStageLogJpaRepository;
import com.example.platform.task.service.TaskStageLogServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStageLogServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldTruncatePreviewTextToFitDatabaseColumn() throws Exception {
        TaskStageLogJpaRepository repository = Mockito.mock(TaskStageLogJpaRepository.class);
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);

        Path logFile = tempDir.resolve("testing.log");
        Files.writeString(logFile, "x".repeat(700));

        Mockito.when(repository.save(Mockito.any(TaskStageLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TaskStageLogServiceImpl service = new TaskStageLogServiceImpl(repository, objectStorageService, "qa-report");

        TaskStageLogEntity entity = service.archiveStageLog(28L, "TESTING", logFile, 1);

        assertThat(entity.getPreviewText()).hasSize(512);
        Mockito.verify(objectStorageService).uploadFile("qa-report", "runs/28/logs/testing.log", logFile);
    }
}
