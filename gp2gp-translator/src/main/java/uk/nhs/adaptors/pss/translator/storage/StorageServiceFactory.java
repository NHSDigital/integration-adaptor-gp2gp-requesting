package uk.nhs.adaptors.pss.translator.storage;

import lombok.Setter;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Setter
public class StorageServiceFactory implements FactoryBean<StorageService> {

    @Autowired
    private StorageServiceConfiguration configuration;

    public StorageService getObject() {

        // we cannot create a private instance of storageService without triggering
        // an EI_EXPOSE_REP via Spotbug tests
        StorageService storageService = null;
        switch (StorageServiceOptionsEnum.enumOf(configuration.getType())) {
            case S3:
                storageService = new AWSStorageService(S3Client.builder().build(), configuration, S3Presigner.builder().build());
                break;
            case AZURE:
                storageService = new AzureStorageService(configuration);
                break;
            default:
                storageService = new LocalStorageService();
        }

        return storageService;
    }

    public Class<?> getObjectType() {
        return StorageService.class;
    }
}