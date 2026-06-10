package com.example.platform.task;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.ArtifactJpaRepository;
import com.example.platform.task.service.TaskArtifactArchiveServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskArtifactArchiveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldUploadArtifactsAndBindMatchedCaseResult() throws Exception {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        Path workspace = tempDir.resolve("workspace");
        Path reportDir = workspace.resolve("playwright-report");
        Path rawArtifactDir = workspace.resolve(".playwright-artifacts/checkout");
        Files.createDirectories(reportDir.resolve("data"));
        Files.createDirectories(rawArtifactDir);
        Files.writeString(reportDir.resolve("index.html"), "<html>ok</html>");
        Files.writeString(rawArtifactDir.resolve("trace.zip"), "zip");

        Mockito.when(objectStorageService.uploadFile(Mockito.anyString(), Mockito.anyString(), Mockito.any(Path.class)))
                .thenAnswer(invocation -> {
                    String objectKey = invocation.getArgument(1, String.class);
                    return "http://minio/" + objectKey;
                });
        Mockito.when(artifactRepository.save(Mockito.any(ArtifactEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        List<ArtifactEntity> artifacts = service.archiveArtifacts(
                101L,
                workspace,
                List.of(".playwright-artifacts", "playwright-report"),
                Map.of(
                        ".playwright-artifacts/checkout/trace.zip",
                        new com.example.platform.task.service.TaskArtifactArchiveService.ArtifactBindingTarget(1001L, "TRACE")));

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts)
                .extracting(ArtifactEntity::getObjectKey)
                .containsExactlyInAnyOrder(
                        "runs/101/artifacts/1001/.playwright-artifacts/checkout/trace.zip",
                        "runs/101/artifacts/unassigned/playwright-report/index.html");
        assertThat(artifacts)
                .extracting(ArtifactEntity::getArtifactType)
                .containsExactlyInAnyOrder("TRACE", "REPORT_FILE");
        assertThat(artifacts)
                .extracting(ArtifactEntity::getCaseResultId)
                .containsExactlyInAnyOrder(1001L, null);

        ArgumentCaptor<ArtifactEntity> captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        Mockito.verify(artifactRepository, Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ArtifactEntity::getTaskId)
                .containsOnly(101L);
    }

    @Test
    void shouldReturnEmptyWhenReportDirectoryMissing() {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        List<ArtifactEntity> artifacts = service.archiveArtifacts(101L, tempDir, List.of("playwright-report"), Map.of());

        assertThat(artifacts).isEmpty();
        Mockito.verifyNoInteractions(objectStorageService, artifactRepository);
    }

    @Test
    void shouldFailWhenUploadFails() throws Exception {
        ObjectStorageService objectStorageService = Mockito.mock(ObjectStorageService.class);
        ArtifactJpaRepository artifactRepository = Mockito.mock(ArtifactJpaRepository.class);

        Path workspace = tempDir.resolve("workspace");
        Path reportDir = workspace.resolve("playwright-report");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("index.html"), "<html>broken</html>");

        Mockito.when(objectStorageService.uploadFile(
                        "qa-report",
                        "runs/101/artifacts/unassigned/playwright-report/index.html",
                        reportDir.resolve("index.html")))
                .thenThrow(new IllegalStateException("upload failed"));

        TaskArtifactArchiveServiceImpl service = new TaskArtifactArchiveServiceImpl(
                objectStorageService,
                artifactRepository,
                "qa-report");

        assertThatThrownBy(() -> service.archiveArtifacts(101L, workspace, List.of("playwright-report"), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload failed");
    }
}
