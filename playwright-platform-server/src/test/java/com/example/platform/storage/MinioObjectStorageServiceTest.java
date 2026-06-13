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
                .thenReturn("http://minio/presigned/report");

        MinioObjectStorageService service = new MinioObjectStorageService(minioClient, "http://127.0.0.1:9000");

        String result = service.createPresignedGetUrl(
                "qa-report",
                "http://127.0.0.1:9000/qa-report/runs/101/report/index.html?X-Amz-Signature=old");

        ArgumentCaptor<GetPresignedObjectUrlArgs> argsCaptor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        Mockito.verify(minioClient).getPresignedObjectUrl(argsCaptor.capture());

        assertThat(result).isEqualTo("http://minio/presigned/report");
        assertThat(argsCaptor.getValue().bucket()).isEqualTo("qa-report");
        assertThat(argsCaptor.getValue().object()).isEqualTo("runs/101/report/index.html");
    }

    @Test
    void shouldReturnOriginalUrlWhenBucketPathCannotBeExtracted() throws Exception {
        MinioClient minioClient = Mockito.mock(MinioClient.class);
        MinioObjectStorageService service = new MinioObjectStorageService(minioClient, "http://127.0.0.1:9000");

        String result = service.createPresignedGetUrl("qa-report", "http://localhost:9000/report/101/index.html");

        assertThat(result).isEqualTo("http://localhost:9000/report/101/index.html");
        Mockito.verify(minioClient, Mockito.never()).getPresignedObjectUrl(Mockito.any(GetPresignedObjectUrlArgs.class));
    }
}
