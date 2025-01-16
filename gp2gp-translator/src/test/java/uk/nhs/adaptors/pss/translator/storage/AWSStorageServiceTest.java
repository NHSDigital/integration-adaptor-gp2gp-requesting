package uk.nhs.adaptors.pss.translator.storage;

import io.findify.s3mock.S3Mock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AWSStorageServiceTest {

    public static final int PORT = 9090;
    private static final String BUCKET_NAME = "s3bucket";
    private static final String FILE_NAME = "test-file.txt";

    private static S3Mock s3Mock;
    private static AWSStorageService awsStorageService;
    private static StorageServiceConfiguration config;
    private static S3Client s3Client;

    @BeforeAll
    static void setUp() {

        s3Mock = new S3Mock.Builder().withPort(PORT).withInMemoryBackend().build();
        s3Mock.start();
        System.out.println("S3Mock started at http://localhost:" + PORT);

        s3Client = S3Client.builder()
            .endpointOverride(URI.create("http://localhost:" + PORT))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("accessKey", "secretKey")))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .region(Region.EU_WEST_2)
            .build();

        config = new StorageServiceConfiguration();
        config.setContainerName(BUCKET_NAME);

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());

        awsStorageService = new AWSStorageService(s3Client, config);
    }

    @Test
    void uploadToStorageTest() throws IOException {
        String uploadContent = "upload-content";

        awsStorageService.uploadFile(FILE_NAME, uploadContent.getBytes(StandardCharsets.UTF_8));

        final var request = GetObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build();
        ResponseInputStream<GetObjectResponse> uploadedObjectInS3 = s3Client.getObject(request);
        String uploadedS3Content = new String(uploadedObjectInS3.readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(uploadContent, uploadedS3Content);
    }

    @Test
    void downloadFromStorageTest() throws IOException {

        String fileContent = "dummy-content";
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                           RequestBody.fromString(fileContent));

        byte[] response = awsStorageService.downloadFile(FILE_NAME);
        String downloadedContent = new String(response, StandardCharsets.UTF_8);

        assertNotNull(response);
        assertEquals(fileContent, downloadedContent);
    }

    @Test
    void deleteFileTest() {

        String fileContent = "dummy-content";
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                           RequestBody.fromString(fileContent));

        awsStorageService.deleteFile(FILE_NAME);

        Exception exception = assertThrows(Exception.class, () -> awsStorageService.downloadFile(FILE_NAME));

        assertEquals("Error occurred downloading from S3 Bucket", exception.getMessage());
    }

    @Test
    void getFileLocationTest() {

        String fileContent = "dummy-content";
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                           RequestBody.fromString(fileContent));

        String response = awsStorageService.getFileLocation(FILE_NAME);

        assertNotNull(response);
    }
}