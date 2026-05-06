package uk.nhs.adaptors.pss.translator.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.BlobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AzureStorageServiceTest {

    private static final String CONTAINER_NAME = "test-container";
    private static final String FILE_NAME = "testfile.txt";
    private static final byte[] FILE_CONTENT = "mock-content".getBytes(StandardCharsets.UTF_8);

    @Mock
    private BlobServiceClient blobServiceClient;
    @Mock
    private StorageServiceConfiguration configuration;
    @Mock
    private BlobContainerClient blobContainerClient;
    @Mock
    private BlobClient blobClient;
    @Mock
    private BlockBlobClient blockBlobClient;
    @Mock
    private BlobProperties blobProperties;

    private AzureStorageService azureStorageService;

    @BeforeEach
    void setUp() {
        when(configuration.getContainerName()).thenReturn(CONTAINER_NAME);
        azureStorageService = new AzureStorageService(blobServiceClient, configuration);
    }

    private void mockBlobClientChain() {
        when(blobServiceClient.getBlobContainerClient(CONTAINER_NAME)).thenReturn(blobContainerClient);
        when(blobContainerClient.getBlobClient(FILE_NAME)).thenReturn(blobClient);
        when(blobClient.getBlockBlobClient()).thenReturn(blockBlobClient);
    }

    @Test
    void uploadFile_SuccessfullyUploadsToAzure() throws StorageException {
        mockBlobClientChain();

        azureStorageService.uploadFile(FILE_NAME, FILE_CONTENT);

        verify(blockBlobClient).upload(any(InputStream.class), eq((long) FILE_CONTENT.length));
    }

    @Test
    void downloadFile_SuccessfullyDownloadsFromAzure() throws StorageException {
        mockBlobClientChain();
        when(blockBlobClient.getProperties()).thenReturn(blobProperties);
        when(blobProperties.getBlobSize()).thenReturn((long) FILE_CONTENT.length);

        doAnswer(invocation -> {
            ByteArrayOutputStream outputStream = invocation.getArgument(0);
            outputStream.write(FILE_CONTENT);
            return null;
        }).when(blockBlobClient).downloadStream(any(ByteArrayOutputStream.class));

        byte[] result = azureStorageService.downloadFile(FILE_NAME);

        assertArrayEquals(FILE_CONTENT, result);
    }

    @Test
    void deleteFile_SuccessfullyDeletesFromAzure() {
        mockBlobClientChain();

        azureStorageService.deleteFile(FILE_NAME);

        verify(blockBlobClient).delete();
    }

    @Test
    void getFileLocation_ReturnsCorrectUrl() {
        mockBlobClientChain();
        String expectedUrl = "https://azuremock.blob.core.windows.net/test-container/testfile.txt";
        when(blockBlobClient.getBlobUrl()).thenReturn(expectedUrl);

        String result = azureStorageService.getFileLocation(FILE_NAME);

        assertEquals(expectedUrl, result);
    }
}