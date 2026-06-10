package com.example.platform.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private final MinioClient minioClient;
    private final String endpoint;

    public MinioObjectStorageService(MinioClient minioClient,
                                     @Value("${platform.storage.minio.endpoint}") String endpoint) {
        this.minioClient = minioClient;
        this.endpoint = endpoint;
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
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, Files.size(sourceFile), -1)
                            .contentType(Files.probeContentType(sourceFile))
                            .build());
            return endpoint + "/" + bucket + "/" + objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload file to object storage", exception);
        }
    }

    @Override
    public String createPresignedGetUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create presigned url", exception);
        }
    }

    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to ensure bucket exists", exception);
        }
    }
}
