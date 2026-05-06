package uk.nhs.adaptors.pss.translator.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class StorageServiceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageServiceConfig.class);

    @Test
    void azureStorageService_IsCreated_WhenTypeIsAzure() {
        // Arrange & Act
        contextRunner.withPropertyValues("storage.type=Azure")
                .withBean(StorageServiceConfiguration.class, () -> {
                    StorageServiceConfiguration config = new StorageServiceConfiguration();
                    config.setAccountReference("account");
                    config.setAccountSecret(Base64.getEncoder().encodeToString("secret".getBytes()));
                    config.setContainerName("container");
                    return config;
                })
                // Assert
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageService.class);
                    assertThat(context).hasBean("azureStorageService");
                    assertThat(context.getBean(StorageService.class)).isInstanceOf(AzureStorageService.class);
                });
    }

    @Test
    void awsStorageService_IsCreated_WhenTypeIsS3() {
        System.setProperty("aws.region", "eu-west-2");

        contextRunner.withPropertyValues("storage.type=S3")
                .withBean(StorageServiceConfiguration.class, StorageServiceConfiguration::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageService.class);
                    assertThat(context).hasBean("awsStorageService");
                    assertThat(context.getBean(StorageService.class)).isInstanceOf(AWSStorageService.class);
                });

        System.clearProperty("aws.region");
    }

    @Test
    void localStorageService_IsCreated_WhenTypeIsLocalMock() {
        contextRunner.withPropertyValues("storage.type=LocalMock")
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageService.class);
                    assertThat(context).hasBean("localStorageService");
                    assertThat(context.getBean(StorageService.class)).isInstanceOf(LocalStorageService.class);
                });
    }

    @Test
    void localStorageService_IsCreated_WhenTypeIsMissing() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageService.class);
                    assertThat(context).hasBean("localStorageService");
                    assertThat(context.getBean(StorageService.class)).isInstanceOf(LocalStorageService.class);
                });
    }

    @Test
    void noStorageService_IsCreated_WhenTypeIsInvalid() {
        contextRunner.withPropertyValues("storage.type=INVALID_TYPE")
                .withBean(StorageServiceConfiguration.class, StorageServiceConfiguration::new)
                .run(context -> assertThat(context).doesNotHaveBean(StorageService.class));
    }
}