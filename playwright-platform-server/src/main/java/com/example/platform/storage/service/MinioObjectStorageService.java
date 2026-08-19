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
 * MinIO 对象存储服务实现类，提供基于 MinIO 的文件存储和检索功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>自动创建所需的存储桶</li>
 *   <li>上传目录及其所有文件到 MinIO</li>
 *   <li>上传单个文件到 MinIO</li>
 *   <li>生成预签名下载 URL（有效期1小时）</li>
 *   <li>获取对象的输入流</li>
 *   <li>删除指定对象</li>
 *   <li>处理对象键的标准化（支持原始键和完整 URL）</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link MinioClient} - MinIO SDK 客户端（内部和公开两个实例）</li>
 * </ul>
 */
@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String DEFAULT_REGION = "us-east-1";

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final String endpoint;
    private final String publicEndpoint;
    private final String region;

    @Autowired
    public MinioObjectStorageService(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            @Value("${platform.storage.minio.endpoint}") String endpoint,
            @Value("${platform.storage.minio.public-endpoint:${platform.storage.minio.endpoint}}") String publicEndpoint,
            @Value("${platform.storage.minio.region:" + DEFAULT_REGION + "}") String region) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
        this.region = normalizeRegion(region);
    }

    /**
     * 兼容构造器，使用同一个客户端作为内部和公开客户端
     *
     * @param minioClient MinIO 客户端
     * @param endpoint 服务端点
     */
    public MinioObjectStorageService(MinioClient minioClient, String endpoint) {
        this(minioClient, minioClient, endpoint, endpoint, DEFAULT_REGION);
    }

    /**
     * 上传整个目录到对象存储
     * 自动遍历目录中的所有文件并逐一上传
     *
     * @param bucket 存储桶名称
     * @param objectPrefix 对象键前缀
     * @param sourceDirectory 源目录路径
     * @return 上传后的 index.html 访问 URL
     */
    @Override
    public String uploadDirectory(String bucket, String objectPrefix, Path sourceDirectory) {
        ensureBucket(bucket);
        try (var walk = Files.walk(sourceDirectory)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                // 计算相对路径并转换为对象键
                Path relative = sourceDirectory.relativize(path);
                uploadFile(bucket, objectPrefix + "/" + relative.toString().replace('\\', '/'), path);
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload report directory", exception);
        }
        return endpoint + "/" + bucket + "/" + objectPrefix + "/index.html";
    }

    /**
     * 上传单个文件到对象存储
     * 自动检测文件的 MIME 类型
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     * @param sourceFile 源文件路径
     * @return 上传后的访问 URL
     */
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

    /**
     * 创建预签名下载 URL
     * 有效期为1小时
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键（可以是原始键或完整 URL）
     * @return 预签名下载 URL
     */
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
                            .region(region)
                            .object(normalizedObjectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
            return signedUrl;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create presigned url", exception);
        }
    }

    /**
     * 获取对象的输入流
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     * @return 对象内容的输入流
     * @throws IllegalArgumentException 如果对象键为空
     */
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

    /**
     * 删除指定对象
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     */
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

    /**
     * 确保指定的存储桶存在，如不存在则自动创建
     *
     * @param bucket 存储桶名称
     */
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

    /**
     * 解析文件的内容类型（MIME 类型）
     * 如果无法检测，则返回默认的 application/octet-stream
     *
     * @param sourceFile 源文件路径
     * @return 文件内容类型
     */
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
     * 将原始对象键或存储 URL 标准化为 MinIO 对象键
     * 支持以下格式：
     * - 原始对象键（如 "avatars/user123.png"）
     * - 完整 URL（如 "http://minio:9000/bucket/avatars/user123.png"）
     * - 带查询参数或锚点的 URL
     *
     * @param bucket 存储桶名称
     * @param objectKeyOrUrl 对象键或完整 URL
     * @return 标准化后的对象键
     */
    private String normalizeObjectKey(String bucket, String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return objectKeyOrUrl;
        }

        String candidate = objectKeyOrUrl.trim();
        // 如果是完整 URL，解析路径提取对象键
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            try {
                URI uri = new URI(candidate);
                String path = uri.getPath();
                if (path == null || path.isBlank()) {
                    return candidate;
                }

                // 从路径中提取存储桶后的部分
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

        // 处理原始对象键格式
        String normalized = candidate;
        // 移除可能的 "bucket/" 前缀
        if (normalized.startsWith(bucket + "/")) {
            normalized = normalized.substring(bucket.length() + 1);
        }
        // 移除开头的斜杠
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // 移除查询参数
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        // 移除锚点
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }

        return normalized.isBlank() ? candidate : normalized;
    }

    /**
     * 规范化区域名称，如果为空则使用默认值
     *
     * @param candidateRegion 候选区域名称
     * @return 规范化后的区域名称
     */
    private String normalizeRegion(String candidateRegion) {
        if (candidateRegion == null || candidateRegion.isBlank()) {
            return DEFAULT_REGION;
        }
        return candidateRegion.trim();
    }
}