package uk.nhs.adaptors.pss.translator.storage;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.util.Base64;
import java.util.Locale;

public class AzureStorageServiceFactory {
    public AzureStorageService Create(StorageServiceConfiguration configuration) {
        BlobServiceClient blobServiceClient;

        if (!configuration.getAccountReference().isEmpty()) {
            String accountReference = configuration.getAccountReference();
            String azureEndpoint = createAzureStorageEndpoint(configuration.getAccountReference());
            String secret = configuration.getAccountSecret();

            if (isBase64(configuration.getAccountSecret())) {
                StorageSharedKeyCredential credentials = createAzureCredentials(accountReference, secret);
                blobServiceClient = createBlobServiceClient(azureEndpoint, credentials);
            } else {
                AzureSasCredential sasCredential = createAzureSASCredentials(secret);
                blobServiceClient = createBlobServiceClientWithSas(azureEndpoint, sasCredential);
            }
        } else {
            blobServiceClient = null;
        }

        return new AzureStorageService(blobServiceClient, configuration.getContainerName());
    }

    private AzureSasCredential createAzureSASCredentials(String signature) {
        return new AzureSasCredential(signature);
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

    private BlobServiceClient createBlobServiceClientWithSas(String endpoint, AzureSasCredential credentials) {
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credentials)
                .buildClient();
    }


    private boolean isBase64(String key) {
        try {
            Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

}
