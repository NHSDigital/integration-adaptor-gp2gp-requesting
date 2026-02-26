package uk.nhs.adaptors.pss.translator.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import uk.nhs.adaptors.pss.translator.validation.ValidStorageServiceConfiguration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "storage")
@Validated
@ValidStorageServiceConfiguration
public class StorageServiceConfiguration {

    private String type;
    private String region;
    private String containerName;
    private String accountReference;
    private String accountSecret;
    private Integer retryLimit;

}