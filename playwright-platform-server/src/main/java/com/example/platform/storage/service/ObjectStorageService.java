package com.example.platform.storage.service;

import java.nio.file.Path;

public interface ObjectStorageService {
    String uploadDirectory(String bucket, String objectPrefix, Path sourceDirectory);
    String uploadFile(String bucket, String objectKey, Path sourceFile);
    String createPresignedGetUrl(String bucket, String objectKey);
}
