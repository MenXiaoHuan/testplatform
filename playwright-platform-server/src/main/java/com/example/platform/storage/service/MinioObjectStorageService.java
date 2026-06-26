package com.example.platform.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stores task artifacts and reports in MinIO-compatible object storage.
 *
 * <p>The service owns bucket creation, upload URL construction, presigned
 * download URL creation, and cleanup for archived task artifacts.
 */
@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final String endpoint;
    private final String publicEndpoint;

    @Autowired
    public MinioObjectStorageService(
                                         @Qualifier("internalMinioClient") MinioClient internalMinioClient,
                                         @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                                       @Value("${platform.storage.minio.endpoint}") String endpoint,
                                       @Value("${platform.storage.minio.public-endpoint:${platform.storage.minio.endpoint}}") String publicEndpoint) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
    }

    public MinioObjectStorageService(MinioClient minioClient, String endpoint) {
        this(minioClient, minioClient, endpoint, endpoint);
    }

    @Override
    public String uploadDirectory(String bucket, String objectPrefix, Path sourceDirectory) {
        ensureBucket(bucket);
        try (var walk = Files.walk(sourceDirectory)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                Path relative = sourceDirectory.relativize(path);
                uploadFile(bucket, objectPrefix + "/" + relative.toString().replace('\\', '/'), path);
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload report directory", exception);
        }
        return endpoint + "/" + bucket + "/" + objectPrefix + "/index.html";
    }

    @Override
    public String uploadFile(String bucket, String objectKey, Path sourceFile) {
        ensureBucket(bucket);
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, Files.size(sourceFile), -1)
                            .contentType(resolveContentType(sourceFile))
                            .build());
            return endpoint + "/" + bucket + "/" + objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload file to object storage", exception);
        }
    }

    @Override
    public String createPresignedGetUrl(String bucket, String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(bucket, objectKey);
        if (normalizedObjectKey == null || normalizedObjectKey.isBlank()) {
            return objectKey;
        }
        try {
            String signedUrl = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(normalizedObjectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
            return signedUrl;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create presigned url", exception);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(bucket, objectKey);
        if (normalizedObjectKey == null || normalizedObjectKey.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        try {
            return internalMinioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(normalizedObjectKey)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read object from storage", exception);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            internalMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete object from storage", exception);
        }
    }

    private void ensureBucket(String bucket) {
        try {
            boolean exists = internalMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                internalMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to ensure bucket exists", exception);
        }
    }

    private String resolveContentType(Path sourceFile) {
        try {
            String contentType = Files.probeContentType(sourceFile);
            return contentType == null || contentType.isBlank()
                    ? DEFAULT_CONTENT_TYPE
                    : contentType;
        } catch (IOException exception) {
            return DEFAULT_CONTENT_TYPE;
        }
    }

    /**
     * Converts either a raw object key or a previously stored object URL into a MinIO object key.
     */
    private String normalizeObjectKey(String bucket, String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return objectKeyOrUrl;
        }

        String candidate = objectKeyOrUrl.trim();
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            try {
                URI uri = new URI(candidate);
                String path = uri.getPath();
                if (path == null || path.isBlank()) {
                    return candidate;
                }

                String bucketPrefix = "/" + bucket + "/";
                int bucketIndex = path.indexOf(bucketPrefix);
                if (bucketIndex < 0) {
                    return null;
                }

                String normalized = path.substring(bucketIndex + bucketPrefix.length());
                return normalized.isBlank() ? candidate : normalized;
            } catch (URISyntaxException ignored) {
                return candidate;
            }
        }

        String normalized = candidate;
        if (normalized.startsWith(bucket + "/")) {
            normalized = normalized.substring(bucket.length() + 1);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }

        return normalized.isBlank() ? candidate : normalized;
    }
}
