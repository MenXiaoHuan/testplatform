package com.example.platform.storage.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置类，负责创建 MinIO 客户端 Bean。
 * 提供内部客户端（用于上传/下载操作）和公开客户端（用于生成预签名 URL）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>配置内部 MinIO 客户端，用于文件上传和下载</li>
 *   <li>配置公开 MinIO 客户端，用于生成公开访问的预签名 URL</li>
 * </ul>
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link MinioClient} - MinIO SDK 客户端</li>
 * </ul>
 */
@Configuration
public class MinioConfig {
    
    /**
     * 创建内部 MinIO 客户端
     * 用于执行文件上传、下载、删除等操作
     *
     * @param endpoint MinIO 服务端点
     * @param accessKey 访问密钥
     * @param secretKey 密钥
     * @return MinIO 客户端实例
     */
    @Bean
    @Qualifier("internalMinioClient")
    public MinioClient minioClient(
            @Value("${platform.storage.minio.endpoint}") String endpoint,
            @Value("${platform.storage.minio.access-key}") String accessKey,
            @Value("${platform.storage.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 创建公开 MinIO 客户端
     * 用于生成公开访问的预签名 URL
     *
     * @param publicEndpoint 公开访问端点，默认与内部端点相同
     * @param accessKey 访问密钥
     * @param secretKey 密钥
     * @return MinIO 客户端实例
     */
    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(
            @Value("${platform.storage.minio.public-endpoint:${platform.storage.minio.endpoint}}") String publicEndpoint,
            @Value("${platform.storage.minio.access-key}") String accessKey,
            @Value("${platform.storage.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}