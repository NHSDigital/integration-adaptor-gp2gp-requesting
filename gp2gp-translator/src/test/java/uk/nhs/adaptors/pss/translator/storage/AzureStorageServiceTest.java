package uk.nhs.adaptors.pss.translator.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.AzuriteContainer;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class AzureStorageServiceTest {

    private static final String CONTAINER_NAME = "AzureContainer";
    private static final String FILE_NAME = "test-file.txt";

    private AzureStorageService azureStorageService;
    private AzuriteContainer azuriteContainer;
    private StorageServiceConfiguration config;

    @BeforeEach
    void setUp() {
        azuriteContainer = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.33.0");
        azuriteContainer.start();

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azuriteContainer.getConnectionString())
                .buildClient();
        blobServiceClient.createBlobContainer(CONTAINER_NAME);

        config = new StorageServiceConfiguration();
        config.setContainerName(CONTAINER_NAME);

        azureStorageService = new AzureStorageService(blobServiceClient, config);
    }

    @AfterEach
    void tearDown() {
        azuriteContainer.stop();
    }

//    @Test
//    void uploadToStorageTest() throws IOException {
//        String uploadContent = "upload-content";
//
//        azureStorageService.uploadFile(FILE_NAME, uploadContent.getBytes(StandardCharsets.UTF_8));
//
//        final var request = GetObjectRequest.builder().bucket(CONTAINER_NAME).key(FILE_NAME).build();
//        ResponseInputStream<GetObjectResponse> uploadedObjectInS3 = blobServiceClient.(request);
//        String uploadedS3Content = new String(uploadedObjectInS3.readAllBytes(), StandardCharsets.UTF_8);
//
//        assertEquals(uploadContent, new String(downloaded, StandardCharsets.UTF_8));
//    }

}
