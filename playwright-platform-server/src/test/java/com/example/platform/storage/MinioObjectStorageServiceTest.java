package com.example.platform.storage;

import com.example.platform.storage.service.MinioObjectStorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MinioObjectStorageServiceTest {
    @Test
    void shouldNormalizeFullMinioUrlBeforeSigning() throws Exception {
        MinioClient minioClient = Mockito.mock(MinioClient.class);
        Mockito.when(minioClient.getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/presigned/artifact");

        MinioObjectStorageService service = new MinioObjectStorageService(minioClient, "http://127.0.0.1:9000");

        String result = service.createPresignedGetUrl(
                "qa-report",
                "http://127.0.0.1:9000/qa-report/runs/101/artifacts/trace.zip?X-Amz-Signature=old");

        ArgumentCaptor<GetPresignedObjectUrlArgs> argsCaptor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        Mockito.verify(minioClient).getPresignedObjectUrl(argsCaptor.capture());

        assertThat(result).isEqualTo("http://minio/presigned/artifact");
        assertThat(argsCaptor.getValue().bucket()).isEqualTo("qa-report");
        assertThat(argsCaptor.getValue().object()).isEqualTo("runs/101/artifacts/trace.zip");
    }

    @Test
    void shouldReturnOriginalUrlWhenBucketPathCannotBeExtracted() throws Exception {
        MinioClient minioClient = Mockito.mock(MinioClient.class);
        MinioObjectStorageService service = new MinioObjectStorageService(minioClient, "http://127.0.0.1:9000");

        String result = service.createPresignedGetUrl("qa-report", "http://localhost:9000/artifacts/101/trace.zip");

        assertThat(result).isEqualTo("http://localhost:9000/artifacts/101/trace.zip");
        Mockito.verify(minioClient, Mockito.never()).getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void shouldReturnUrlSignedByPublicMinioClientWithoutRewritingHost() throws Exception {
        MinioClient internalMinioClient = Mockito.mock(MinioClient.class);
        MinioClient publicMinioClient = Mockito.mock(MinioClient.class);
        Mockito.when(publicMinioClient.getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio:9000/qa-report/runs/101/logs/testing.log?X-Amz-Signature=test");

        MinioObjectStorageService service = new MinioObjectStorageService(
                internalMinioClient,
                publicMinioClient,
                "http://minio:9000",
                "http://localhost:10000");

        String result = service.createPresignedGetUrl("qa-report", "runs/101/logs/testing.log");

        assertThat(result).isEqualTo("http://minio:9000/qa-report/runs/101/logs/testing.log?X-Amz-Signature=test");
        Mockito.verify(publicMinioClient).getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class));
        Mockito.verify(internalMinioClient, Mockito.never()).getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void shouldUsePublicMinioClientToCreatePresignedUrl() throws Exception {
        MinioClient internalMinioClient = Mockito.mock(MinioClient.class);
        MinioClient publicMinioClient = Mockito.mock(MinioClient.class);
        Mockito.when(publicMinioClient.getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:10000/qa-report/runs/101/artifacts/trace.zip?X-Amz-Signature=test");

        MinioObjectStorageService service = new MinioObjectStorageService(
                internalMinioClient,
                publicMinioClient,
                "http://minio:9000",
                "http://localhost:10000");

        String result = service.createPresignedGetUrl("qa-report", "runs/101/artifacts/trace.zip");

        assertThat(result).isEqualTo("http://localhost:10000/qa-report/runs/101/artifacts/trace.zip?X-Amz-Signature=test");
        Mockito.verify(publicMinioClient).getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class));
        Mockito.verifyNoInteractions(internalMinioClient);
    }
}
