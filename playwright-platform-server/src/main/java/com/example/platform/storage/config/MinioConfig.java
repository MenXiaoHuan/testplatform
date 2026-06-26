package com.example.platform.storage.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
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
