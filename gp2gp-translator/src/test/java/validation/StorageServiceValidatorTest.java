package validation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import uk.nhs.adaptors.pss.translator.storage.StorageServiceConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class StorageServiceValidatorTest {

    // Valid configurations
    private static final String STORAGE_TYPE_S3 = "S3";
    private static final String STORAGE_TYPE_AZURE = "Azure";
    private static final String VALID_STORAGE_REGION = "eu-west-2";
    private static final String VALID_STORAGE_CONTAINER_NAME = "some-password";
    private static final String VALID_STORAGE_REFERENCE = "some-reference";
    private static final String VALID_STORAGE_SECRET = "some-secret";

    private static final String EMPTY_STRING = "";

    // Configuration fields
    private static final String STORAGE_TYPE = "type";
    private static final String STORAGE_REGION = "region";
    private static final String STORAGE_CONTAINER_NAME = "containerName";
    private static final String STORAGE_REFERENCE = "accountReference";
    private static final String STORAGE_SECRET = "accountSecret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestStorageServiceConfiguration.class);

    @Test
    void When_ConfigurationContainsS3RequiredProperties_Expect_ContextIsCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, STORAGE_TYPE_S3),
                        buildPropertyValue(STORAGE_REGION, VALID_STORAGE_REGION),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, VALID_STORAGE_CONTAINER_NAME),
                        buildPropertyValue(STORAGE_REFERENCE, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_SECRET, StringUtils.EMPTY)
                )
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(StorageServiceConfiguration.class);

                    var storageServiceConfiguration = context.getBean(StorageServiceConfiguration.class);

                    assertAll(
                            () -> assertThat(storageServiceConfiguration.getType())
                                    .isEqualTo(STORAGE_TYPE_S3),
                            () -> assertThat(storageServiceConfiguration.getRegion())
                                    .isEqualTo(VALID_STORAGE_REGION),
                            () -> assertThat(storageServiceConfiguration.getContainerName())
                                    .isEqualTo(VALID_STORAGE_CONTAINER_NAME),
                            () -> assertThat(storageServiceConfiguration.getAccountReference())
                                    .isEmpty(),
                            () -> assertThat(storageServiceConfiguration.getAccountSecret())
                                    .isEmpty()
                    );
                });
    }

    @Test
    void When_ConfigurationHasMissingPropertiesAndTypeIsS3_Expect_ContextIsNotCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, STORAGE_TYPE_S3),
                        buildPropertyValue(STORAGE_REGION, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_REFERENCE, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_SECRET, StringUtils.EMPTY)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    var startupFailure = context.getStartupFailure();

                    assertThat(startupFailure)
                            .rootCause()
                            .hasMessageContaining("Env variable not provided: STORAGE_REGION")
                            .hasMessageContaining("Env variable not provided: STORAGE_CONTAINER_NAME");
                });
    }

    @Test
    void When_ConfigurationContainsAzureRequiredProperties_Expect_IsContextIsCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, STORAGE_TYPE_AZURE),
                        buildPropertyValue(STORAGE_REGION, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, VALID_STORAGE_CONTAINER_NAME),
                        buildPropertyValue(STORAGE_REFERENCE, VALID_STORAGE_REFERENCE),
                        buildPropertyValue(STORAGE_SECRET, VALID_STORAGE_SECRET)
                )
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(StorageServiceConfiguration.class);

                    var storageServiceConfiguration = context.getBean(StorageServiceConfiguration.class);

                    assertAll(
                            () -> assertThat(storageServiceConfiguration.getType())
                                    .isEqualTo(STORAGE_TYPE_AZURE),
                            () -> assertThat(storageServiceConfiguration.getRegion())
                                    .isEmpty(),
                            () -> assertThat(storageServiceConfiguration.getContainerName())
                                    .isEqualTo(VALID_STORAGE_CONTAINER_NAME),
                            () -> assertThat(storageServiceConfiguration.getAccountReference())
                                    .isEqualTo(VALID_STORAGE_REFERENCE),
                            () -> assertThat(storageServiceConfiguration.getAccountSecret())
                                    .isEqualTo(VALID_STORAGE_SECRET)
                    );
                });
    }

    @Test
    void When_ConfigurationHasMissingPropertiesAndTypeIsAzure_Expect_ContextIsNotCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, STORAGE_TYPE_AZURE),
                        buildPropertyValue(STORAGE_REGION, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_REFERENCE, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_SECRET, StringUtils.EMPTY)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    var startupFailure = context.getStartupFailure();

                    assertThat(startupFailure)
                            .rootCause()
                            .hasMessageContaining("Env variable not provided: STORAGE_CONTAINER_NAME")
                            .hasMessageContaining("Env variable not provided: STORAGE_REFERENCE")
                            .hasMessageContaining("Env variable not provided: STORAGE_SECRET");
                });
    }

    @Test
    void When_ConfigurationContainsAllPropertiesAndTypeIsAzure_Expect_ContextIsNotCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, STORAGE_TYPE_AZURE),
                        buildPropertyValue(STORAGE_REGION, VALID_STORAGE_REGION),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, VALID_STORAGE_CONTAINER_NAME),
                        buildPropertyValue(STORAGE_REFERENCE, VALID_STORAGE_REFERENCE),
                        buildPropertyValue(STORAGE_SECRET, VALID_STORAGE_SECRET)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    var startupFailure = context.getStartupFailure();

                    assertThat(startupFailure)
                            .rootCause()
                            .hasMessageContaining("Env variable not required when using storage type Azure: STORAGE_REGION");
                });
    }

    @Test
    void When_ConfigurationDoesNotContainType_Expect_ContextIsNotCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(STORAGE_TYPE, StringUtils.EMPTY),
                        buildPropertyValue(STORAGE_REGION, VALID_STORAGE_REGION),
                        buildPropertyValue(STORAGE_CONTAINER_NAME, VALID_STORAGE_CONTAINER_NAME),
                        buildPropertyValue(STORAGE_REFERENCE, VALID_STORAGE_REFERENCE),
                        buildPropertyValue(STORAGE_SECRET, VALID_STORAGE_SECRET)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    var startupFailure = context.getStartupFailure();

                    assertThat(startupFailure)
                            .rootCause()
                            .hasMessageContaining("Env variable not provided: STORAGE_TYPE");
                });
    }

    @Contract(pure = true)
    private static @NotNull String buildPropertyValue(String propertyName, String value) {
        return String.format("storage.%s=%s", propertyName, value);
    }

    @Configuration
    @EnableConfigurationProperties(StorageServiceConfiguration.class)
    static class TestStorageServiceConfiguration {
    }
}



