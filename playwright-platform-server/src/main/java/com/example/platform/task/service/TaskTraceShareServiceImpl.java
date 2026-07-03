package com.example.platform.task.service;

import com.example.platform.storage.service.ObjectStorageService;
import com.example.platform.task.dto.TaskTraceShareResponse;
import com.example.platform.task.mapper.ArtifactMapper;
import com.example.platform.task.mapper.TaskMapper;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.TaskEntity;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskTraceShareServiceImpl implements TaskTraceShareService {
    static final String TRACE_SHARE_DOWNLOAD_PATH = "/api/public/traces/download";
    private static final String TRACE_ARTIFACT_TYPE = "TRACE";

    private final TaskMapper taskMapper;
    private final ArtifactMapper artifactMapper;
    private final ObjectStorageService objectStorageService;
    private final String storageBucket;
    private final String traceShareSecret;
    private final Duration traceShareTtl;
    private final Clock clock;
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    @Autowired
    public TaskTraceShareServiceImpl(
            TaskMapper taskMapper,
            ArtifactMapper artifactMapper,
            ObjectStorageService objectStorageService,
            @Value("${platform.storage.bucket:qa-report}") String storageBucket,
            @Value("${platform.task.trace-share.secret:${platform.storage.minio.secret-key}}") String traceShareSecret,
            @Value("${platform.task.trace-share.ttl-seconds:300}") long traceShareTtlSeconds) {
        this(
                taskMapper,
                artifactMapper,
                objectStorageService,
                storageBucket,
                traceShareSecret,
                Duration.ofSeconds(traceShareTtlSeconds),
                Clock.systemUTC());
    }

    TaskTraceShareServiceImpl(
            TaskMapper taskMapper,
            ArtifactMapper artifactMapper,
            ObjectStorageService objectStorageService,
            String storageBucket,
            String traceShareSecret,
            Duration traceShareTtl,
            Clock clock) {
        this.taskMapper = taskMapper;
        this.artifactMapper = artifactMapper;
        this.objectStorageService = objectStorageService;
        this.storageBucket = storageBucket;
        this.traceShareSecret = requireSecret(traceShareSecret);
        this.traceShareTtl = traceShareTtl == null || traceShareTtl.isZero() || traceShareTtl.isNegative()
                ? Duration.ofMinutes(5)
                : traceShareTtl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public TaskTraceShareResponse createTraceShare(Long spaceId, Long taskId, Long artifactId) {
        requireTaskInSpace(spaceId, taskId);
        ArtifactEntity traceArtifact = requireTraceArtifact(taskId, artifactId);
        Instant expiresAt = clock.instant().plus(traceShareTtl);
        String token = signToken(taskId, traceArtifact.getId(), expiresAt);
        return new TaskTraceShareResponse(
                TRACE_SHARE_DOWNLOAD_PATH + "?token=" + token,
                expiresAt);
    }

    @Override
    public ResponseEntity<Resource> downloadSharedTrace(String token) {
        TraceSharePayload payload = verifyToken(token);
        taskMapper.findById(payload.taskId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        ArtifactEntity traceArtifact = requireTraceArtifact(payload.taskId(), payload.artifactId());
        return buildStorageDownloadResponse(
                traceArtifact.getBucket(),
                traceArtifact.getObjectKey(),
                traceArtifact.getContentType(),
                traceArtifact.getSize());
    }

    private TaskEntity requireTaskInSpace(Long spaceId, Long taskId) {
        return taskMapper.findByIdAndSpaceId(taskId, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private ArtifactEntity requireTraceArtifact(Long taskId, Long artifactId) {
        ArtifactEntity artifact = artifactMapper.findAllByTaskIdOrderByIdAsc(taskId).stream()
                .filter(item -> artifactId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace artifact not found"));
        if (!TRACE_ARTIFACT_TYPE.equalsIgnoreCase(artifact.getArtifactType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact is not a trace");
        }
        return artifact;
    }

    private String signToken(Long taskId, Long artifactId, Instant expiresAt) {
        String payload = "%d:%d:%d".formatted(taskId, artifactId, expiresAt.getEpochSecond());
        byte[] signature = sign(payload);
        return urlEncoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + urlEncoder.encodeToString(signature);
    }

    private TraceSharePayload verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw invalidToken();
        }

        try {
            String payload = new String(urlDecoder.decode(parts[0]), StandardCharsets.UTF_8);
            byte[] providedSignature = urlDecoder.decode(parts[1]);
            byte[] expectedSignature = sign(payload);
            if (!MessageDigest.isEqual(providedSignature, expectedSignature)) {
                throw invalidToken();
            }

            String[] payloadParts = payload.split(":");
            if (payloadParts.length != 3) {
                throw invalidToken();
            }

            Long taskId = Long.parseLong(payloadParts[0]);
            Long artifactId = Long.parseLong(payloadParts[1]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[2]));
            if (!clock.instant().isBefore(expiresAt)) {
                throw invalidToken();
            }
            return new TraceSharePayload(taskId, artifactId, expiresAt);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(traceShareSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign trace share token", exception);
        }
    }

    private ResponseEntity<Resource> buildStorageDownloadResponse(
            String bucket,
            String objectKey,
            String contentType,
            Long size) {
        String resolvedBucket = bucket == null || bucket.isBlank() ? storageBucket : bucket;
        if (resolvedBucket == null || resolvedBucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace artifact is not available");
        }

        InputStream inputStream = objectStorageService.getObject(resolvedBucket, objectKey);
        InputStreamResource resource = new InputStreamResource(inputStream);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        "Content-Disposition",
                        ContentDisposition.attachment()
                                .filename(extractFilename(objectKey), StandardCharsets.UTF_8)
                                .build()
                                .toString());
        if (size != null && size >= 0) {
            response.contentLength(size);
        }
        return response.body(resource);
    }

    private String extractFilename(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == objectKey.length() - 1) {
            return objectKey;
        }
        return objectKey.substring(slashIndex + 1);
    }

    private String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Trace share secret must not be blank");
        }
        return secret;
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid trace share token");
    }

    private record TraceSharePayload(Long taskId, Long artifactId, Instant expiresAt) {
    }
}
