package uk.nhs.adaptors.pss.translator.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AWSStorageServiceTest {

    private static final String FILE_NAME = "testfile.txt";
    private static final String BUCKET_NAME = "test-bucket";
    private static final byte[] FILE_CONTENT = "mock-content".getBytes(StandardCharsets.UTF_8);
    private static final String PRESIGNED_URL = "https://s3.amazonaws.com/testfile.txt";

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedGetObjectRequest presignedGetObjectRequest;

    private AWSStorageService awsStorageService;

    @BeforeEach
    void setUp() {
        StorageServiceConfiguration config = new StorageServiceConfiguration();
        config.setContainerName(BUCKET_NAME);
        awsStorageService = new AWSStorageService(s3Client, config, s3Presigner);
    }

    @Test
    void When_UploadFile_Expect_SuccessfullyUploadsToS3() throws StorageException {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        awsStorageService.uploadFile(FILE_NAME, FILE_CONTENT);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void When_UploadFile_AndS3Throws_Expect_StorageExceptionThrown() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThrows(StorageException.class, () -> awsStorageService.uploadFile(FILE_NAME, FILE_CONTENT));
    }

    @Test
    void When_DownloadFile_Expect_SuccessfullyDownloadsFromS3() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseInputStream(FILE_CONTENT));

        byte[] result = awsStorageService.downloadFile(FILE_NAME);

        assertArrayEquals(FILE_CONTENT, result);
    }

    @Test
    void When_DownloadFile_AndS3Throws_Expect_StorageExceptionThrown() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThrows(StorageException.class, () -> awsStorageService.downloadFile(FILE_NAME));
    }

    @Test
    void When_DeleteFile_Expect_SuccessfullyDeletesFromS3() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        awsStorageService.deleteFile(FILE_NAME);

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void When_DeleteFile_AndS3Throws_Expect_StorageExceptionThrown() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThrows(StorageException.class, () -> awsStorageService.deleteFile(FILE_NAME));
    }

    @Test
    void When_GetFileLocation_Expect_ReturnsPresignedUrl() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(new URL(PRESIGNED_URL));

        String result = awsStorageService.getFileLocation(FILE_NAME);

        assertEquals(PRESIGNED_URL, result);
    }

    private ResponseInputStream<GetObjectResponse> responseInputStream(byte[] content) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(content)
        );
    }
}