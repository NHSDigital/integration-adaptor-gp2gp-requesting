package uk.nhs.adaptors.pss.translator.storage;

import io.findify.s3mock.S3Mock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AWSStorageServiceIntegrationTest {

    private static final int PORT = 9090;
    private static final String BUCKET_NAME = "s3bucket";
    private static final String FILE_NAME = "test-file.txt";
    private static final String ACCESS_KEY = "accessKey";
    private static final String SECRET_KEY = "secretKey";
    private static final URI ENDPOINT = URI.create("http://localhost:" + PORT);

    private S3Mock s3Mock;
    private AWSStorageService awsStorageService;
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        s3Mock = new S3Mock.Builder().withPort(PORT).withInMemoryBackend().build();
        s3Mock.start();

        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));

        s3Client = S3Client.builder()
                .endpointOverride(ENDPOINT)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .region(Region.EU_WEST_2)
                .build();

        S3Presigner s3Presigner = S3Presigner.builder()
                .endpointOverride(ENDPOINT)
                .credentialsProvider(credentialsProvider)
                .region(Region.EU_WEST_2)
                .build();

        StorageServiceConfiguration config = new StorageServiceConfiguration();
        config.setContainerName(BUCKET_NAME);
        config.setRegion(Region.EU_WEST_2.toString());

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());

        awsStorageService = new AWSStorageService(s3Client, config, s3Presigner);
    }

    @AfterEach
    void tearDown() {
        s3Mock.stop();
    }

    @Test
    void uploadToStorageTest() {
        String uploadContent = "upload-content";

        awsStorageService.uploadFile(FILE_NAME, uploadContent.getBytes(StandardCharsets.UTF_8));

        byte[] downloaded = s3Client.getObjectAsBytes(r -> r.bucket(BUCKET_NAME).key(FILE_NAME)).asByteArray();

        Assertions.assertEquals(uploadContent, new String(downloaded, StandardCharsets.UTF_8));
    }

    @Test
    void downloadFromStorageTest() {
        String fileContent = "dummy-content";

        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                RequestBody.fromString(fileContent));

        byte[] response = awsStorageService.downloadFile(FILE_NAME);

        assertNotNull(response);
        Assertions.assertEquals(fileContent, new String(response, StandardCharsets.UTF_8));
    }

    @Test
    void deleteFileTest() {
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                RequestBody.fromString("dummy-content"));

        awsStorageService.deleteFile(FILE_NAME);

        Exception exception = assertThrows(Exception.class, () -> awsStorageService.downloadFile(FILE_NAME));

        Assertions.assertEquals("Error occurred downloading from S3 Bucket", exception.getMessage());
    }

    @Test
    void getFileLocationTest() {
        s3Client.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(FILE_NAME).build(),
                RequestBody.fromString("dummy-content"));

        String response = awsStorageService.getFileLocation(FILE_NAME);

        assertNotNull(response);
        Assertions.assertTrue(response.contains(FILE_NAME));
        Assertions.assertTrue(response.contains(BUCKET_NAME));
    }
}