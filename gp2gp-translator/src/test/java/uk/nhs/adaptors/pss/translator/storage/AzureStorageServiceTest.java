package uk.nhs.adaptors.pss.translator.storage;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.AzuriteContainer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AzureStorageServiceTest {

    private static final String CONTAINER_NAME = "azurecontainer";
    private static final String FILE_NAME = "testfile.txt";

    private AzureStorageService azureStorageService;
    private AzuriteContainer azuriteContainer;
    private StorageServiceConfiguration config;
    private BlobServiceClient blobServiceClient;

    @BeforeEach
    void setUp() {
        azuriteContainer = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.33.0");
        azuriteContainer.start();

        blobServiceClient = new BlobServiceClientBuilder()
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

    @Test
    void uploadToStorageTest() throws IOException {
        String uploadContent = "uploadcontent";

        azureStorageService.uploadFile(FILE_NAME, uploadContent.getBytes(StandardCharsets.UTF_8));

        byte[] downloaded = blobServiceClient
                .getBlobContainerClient(CONTAINER_NAME)
                .getBlobClient(FILE_NAME).downloadContent().toBytes();

        Assertions.assertEquals(uploadContent, new String(downloaded, StandardCharsets.UTF_8));
    }

    @Test
    void downloadFromStorageTest() {

        String fileContent = "dummy-content";

        blobServiceClient
                .getBlobContainerClient(CONTAINER_NAME)
                .getBlobClient(FILE_NAME).upload(BinaryData.fromString(fileContent));

        byte[] response = azureStorageService.downloadFile(FILE_NAME);
        String downloadedContent = new String(response, StandardCharsets.UTF_8);

        assertNotNull(response);
        assertEquals(fileContent, downloadedContent);
    }
}
