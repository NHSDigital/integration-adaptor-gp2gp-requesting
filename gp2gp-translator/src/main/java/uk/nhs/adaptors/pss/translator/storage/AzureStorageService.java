package uk.nhs.adaptors.pss.translator.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "BlobServiceClient is immutable and thread-safe.")
public class AzureStorageService implements StorageService {

    private final BlobServiceClient blobServiceClient;
    private final String containerName;

    public AzureStorageService(BlobServiceClient client, StorageServiceConfiguration config) {
        blobServiceClient = client;
        containerName = config.getContainerName();
    }

    public void uploadFile(String filename, byte[] fileAsString) throws StorageException {
        try {
            addFileStringToMainContainer(filename, fileAsString);
        } catch (IOException e) {
            throw new StorageException("Failed adding file to Azure Blob storage", e);
        }
    }

    public byte[] downloadFile(String filename) throws StorageException {
        ByteArrayOutputStream stream = downloadFileToStream(filename);
        return stream.toByteArray();
    }

    public void deleteFile(String filename) {
        BlobContainerClient containerClient = createBlobContainerClient();
        BlockBlobClient blobClient = containerClient.getBlobClient(filename).getBlockBlobClient();
        blobClient.delete();
    }

    public String getFileLocation(String filename) {
        var blobClient = createBlobBlockClient(filename);
        return blobClient.getBlobUrl();
    }

    private StorageSharedKeyCredential createAzureCredentials(String accountName, String accountKey) {
        return new StorageSharedKeyCredential(accountName, accountKey);
    }

    private String createAzureStorageEndpoint(String containerName) {
        return String.format(Locale.ROOT, "https://%s.blob.core.windows.net", containerName);
    }

    private BlobServiceClient createBlobServiceClient(String endpoint, StorageSharedKeyCredential credentials) {

        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credentials)
                .buildClient();
    }

    private BlobContainerClient createBlobContainerClient() {
        return blobServiceClient.getBlobContainerClient(containerName);
    }

    private BlockBlobClient createBlobBlockClient(String filename) {
        BlobContainerClient containerClient = createBlobContainerClient();
        return containerClient.getBlobClient(filename).getBlockBlobClient();
    }

    private void addFileStringToMainContainer(String filename, byte[] fileAsString) throws StorageException, IOException {
        try (InputStream dataStream = new ByteArrayInputStream(fileAsString)) {
            BlockBlobClient blobClient = createBlobBlockClient(filename);
            blobClient.upload(dataStream, fileAsString.length);
        } catch (IOException e) {
            throw new StorageException("Failed to upload blob to Azure Blob storage", e);
        }
    }

    private ByteArrayOutputStream downloadFileToStream(String filename) throws StorageException {

        BlockBlobClient blobClient = createBlobBlockClient(filename);
        int dataSize = (int) blobClient.getProperties().getBlobSize();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(dataSize)) {
            blobClient.downloadStream(outputStream);
            return outputStream;
        } catch (IOException e) {
            throw new StorageException("Failed to download blob from Azure Blob storage", e);
        }
    }

}
