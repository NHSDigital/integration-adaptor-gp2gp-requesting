package uk.nhs.adaptors.pss.translator.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.specialized.BlockBlobClient;

public class AzureStorageService implements StorageService {
    
    // Consistent objects
    protected final BlobServiceClient blobServiceClient;
    protected String containerName;

    public AzureStorageService(BlobServiceClient blobServiceClient, String containerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
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

    private BlockBlobClient createBlobBlockClient(String filename) {
        BlobContainerClient containerClient = createBlobContainerClient();
        return containerClient.getBlobClient(filename).getBlockBlobClient();
    }

    private BlobContainerClient createBlobContainerClient() {
        return blobServiceClient.getBlobContainerClient(containerName);
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
