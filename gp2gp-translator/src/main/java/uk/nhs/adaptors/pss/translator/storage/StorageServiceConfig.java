package uk.nhs.adaptors.pss.translator.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Locale;

@Configuration
public class StorageServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "Azure")
    public @Nonnull StorageService azureStorageService(StorageServiceConfiguration configuration) {
        StorageSharedKeyCredential credentials = new StorageSharedKeyCredential(
                configuration.getAccountReference(),
                configuration.getAccountSecret()
        );

        String endpoint = String.format(
                Locale.ROOT,
                "https://%s.blob.core.windows.net",
                configuration.getAccountReference()
        );

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credentials)
                .buildClient();

        return new AzureStorageService(blobServiceClient, configuration);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "S3")
    public @Nonnull StorageService awsStorageService(StorageServiceConfiguration configuration) {
        return new AWSStorageService(
                S3Client.builder().build(),
                configuration,
                S3Presigner.builder().build()
        );
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "LocalMock", matchIfMissing = true)
    public @Nonnull StorageService localStorageService() {
        return new LocalStorageService();
    }
}
