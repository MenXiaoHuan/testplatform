package com.example.platform.task.service;

import com.example.platform.task.dto.TaskTraceShareResponse;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.TaskEntity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTraceShareServiceImplTest {
    @Test
    void shouldCreateShareUrlAndDownloadTraceThroughProxy() throws Exception {
        TaskMapper taskMapper = Mockito.mock(TaskMapper.class);
        ArtifactMapper artifactMapper = Mockito.mock(ArtifactMapper.class);
        com.example.platform.storage.service.ObjectStorageService objectStorageService =
                Mockito.mock(com.example.platform.storage.service.ObjectStorageService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-03T10:10:30Z"), ZoneOffset.UTC);
        TaskTraceShareServiceImpl service = new TaskTraceShareServiceImpl(
                taskMapper,
                artifactMapper,
                objectStorageService,
                "qa-report",
                "trace-share-secret",
                Duration.ofMinutes(5),
                clock);

        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setSpaceId(7L);
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(11L);
        artifact.setTaskId(101L);
        artifact.setArtifactType("TRACE");
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");
        artifact.setContentType("application/zip");
        artifact.setSize(10L);

        Mockito.when(taskMapper.findByIdAndSpaceId(101L, 7L)).thenReturn(Optional.of(task));
        Mockito.when(taskMapper.findById(101L)).thenReturn(Optional.of(task));
        Mockito.when(artifactMapper.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));
        Mockito.when(objectStorageService.getObject("qa-report", "runs/101/artifacts/trace.zip"))
                .thenReturn(new ByteArrayInputStream("trace-data".getBytes(StandardCharsets.UTF_8)));

        TaskTraceShareResponse response = service.createTraceShare(7L, 101L, 11L);
        String token = response.shareUrl().substring(response.shareUrl().indexOf("token=") + 6);

        assertThat(response.shareUrl()).startsWith("/api/public/traces/download?token=");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-07-03T10:15:30Z"));

        ResponseEntity<org.springframework.core.io.Resource> downloadResponse = service.downloadSharedTrace(token);

        assertThat(downloadResponse.getHeaders().getContentType()).hasToString("application/zip");
        assertThat(downloadResponse.getHeaders().getFirst("Content-Disposition")).contains("trace.zip");
        assertThat(new String(((InputStreamResource) downloadResponse.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("trace-data");
    }

    @Test
    void shouldRejectExpiredTraceShareToken() {
        TaskMapper taskMapper = Mockito.mock(TaskMapper.class);
        ArtifactMapper artifactMapper = Mockito.mock(ArtifactMapper.class);
        com.example.platform.storage.service.ObjectStorageService objectStorageService =
                Mockito.mock(com.example.platform.storage.service.ObjectStorageService.class);
        TaskEntity task = new TaskEntity();
        task.setId(101L);
        task.setSpaceId(7L);
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(11L);
        artifact.setTaskId(101L);
        artifact.setArtifactType("TRACE");
        artifact.setBucket("qa-report");
        artifact.setObjectKey("runs/101/artifacts/trace.zip");

        Mockito.when(taskMapper.findByIdAndSpaceId(101L, 7L)).thenReturn(Optional.of(task));
        Mockito.when(artifactMapper.findAllByTaskIdOrderByIdAsc(101L)).thenReturn(List.of(artifact));

        TaskTraceShareServiceImpl issuingService = new TaskTraceShareServiceImpl(
                taskMapper,
                artifactMapper,
                objectStorageService,
                "qa-report",
                "trace-share-secret",
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-07-03T10:10:30Z"), ZoneOffset.UTC));
        TaskTraceShareServiceImpl verifyingService = new TaskTraceShareServiceImpl(
                taskMapper,
                artifactMapper,
                objectStorageService,
                "qa-report",
                "trace-share-secret",
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-07-03T10:16:00Z"), ZoneOffset.UTC));

        TaskTraceShareResponse response = issuingService.createTraceShare(7L, 101L, 11L);
        String token = response.shareUrl().substring(response.shareUrl().indexOf("token=") + 6);

        assertThatThrownBy(() -> verifyingService.downloadSharedTrace(token))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("invalid trace share token");
    }
}
