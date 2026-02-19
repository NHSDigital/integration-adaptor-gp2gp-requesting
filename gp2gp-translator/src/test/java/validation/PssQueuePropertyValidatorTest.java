package validation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import uk.nhs.adaptors.pss.translator.config.PssQueueProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class PssQueuePropertyValidatorTest {

    // Valid configurations
    private static final String VALID_PS_AMQP_BROKER = "amqp://localhost:1234";
    private static final String VALID_PS_AMQP_USERNAME = "some-username";
    private static final String VALID_PS_AMQP_PASSWORD = "some-password";

    // Configuration fields
    private static final String PS_AMQP_BROKER = "broker";
    private static final String PS_AMQP_USERNAME = "username";
    private static final String PS_AMQP_PASSWORD = "password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestPssQueueProperties.class);

    @Test
    void When_ConfigurationContainsAllProperties_Expect_IsContextIsCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(PS_AMQP_BROKER, VALID_PS_AMQP_BROKER),
                        buildPropertyValue(PS_AMQP_USERNAME, VALID_PS_AMQP_USERNAME),
                        buildPropertyValue(PS_AMQP_PASSWORD, VALID_PS_AMQP_PASSWORD)
                )
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(PssQueueProperties.class);

                    var storageConnectorConfiguration = context.getBean(PssQueueProperties.class);

                    assertAll(
                            () -> assertThat(storageConnectorConfiguration.getBroker())
                                    .isEqualTo(VALID_PS_AMQP_BROKER),
                            () -> assertThat(storageConnectorConfiguration.getUsername())
                                    .isEqualTo(VALID_PS_AMQP_USERNAME),
                            () -> assertThat(storageConnectorConfiguration.getPassword())
                                    .isEqualTo(VALID_PS_AMQP_PASSWORD)
                    );
                });
    }

    @Test
    void When_ConfigurationPropertiesNotProvided_Expect_ContextNotCreated() {
        contextRunner
                .withPropertyValues(
                        buildPropertyValue(PS_AMQP_BROKER, ""),
                        buildPropertyValue(PS_AMQP_USERNAME, ""),
                        buildPropertyValue(PS_AMQP_PASSWORD, "")
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    var startupFailure = context.getStartupFailure();

                    assertThat(startupFailure)
                            .rootCause()
                            .hasMessageContaining("Env variable not provided: PS_AMQP_BROKER")
                            .hasMessageContaining("Env variable not provided: PS_AMQP_USERNAME")
                            .hasMessageContaining("Env variable not provided: PS_AMQP_PASSWORD");
                });
    }

//    @Test
//    void When_StorageTypeIsNotAzureAndConnectionStringIsEmpty_Expect_ContextCreated() {
//        contextRunner
//                .withPropertyValues(
//                        buildPropertyValue(GP2GP_STORAGE_TYPE, VALID_GP2GP_STORAGE_TYPE),
//                        buildPropertyValue(GP2GP_STORAGE_CONTAINER_NAME, VALID_GP2GP_STORAGE_CONTAINER_NAME),
//                        buildPropertyValue(GP2GP_AZURE_STORAGE_CONNECTION_STRING, "")
//                )
//                .run(context -> {
//                    var storageConnectorConfiguration = context.getBean(StorageConnectorConfiguration.class);
//
//                    assertAll(
//                            () -> assertThat(storageConnectorConfiguration.getType())
//                                    .isEqualTo(VALID_GP2GP_STORAGE_TYPE),
//                            () -> assertThat(storageConnectorConfiguration.getContainerName())
//                                    .isEqualTo(VALID_GP2GP_STORAGE_CONTAINER_NAME),
//                            () -> assertThat(storageConnectorConfiguration.getAzureConnectionString())
//                                    .isEmpty()
//                    );
//                });
//    }
//
//    @Test
//    void When_StorageTypeIsAzureAndConnectionStringIsEmpty_Expect_IsNotContextCreated() {
//        contextRunner
//                .withPropertyValues(
//                        buildPropertyValue(GP2GP_STORAGE_TYPE, "Azure"),
//                        buildPropertyValue(GP2GP_STORAGE_CONTAINER_NAME, VALID_GP2GP_STORAGE_CONTAINER_NAME),
//                        buildPropertyValue(GP2GP_AZURE_STORAGE_CONNECTION_STRING, "")
//                )
//                .run(context -> {
//                    assertThat(context).hasFailed();
//                    var startupFailure = context.getStartupFailure();
//
//                    assertThat(startupFailure)
//                            .rootCause()
//                            .hasMessageContaining("Env variable not provided: GP2GP_AZURE_STORAGE_CONNECTION_STRING");
//                });
//    }

    @Contract(pure = true)
    private static @NotNull String buildPropertyValue(String propertyName, String value) {
        return String.format("amqp.pss.%s=%s", propertyName, value);
    }

    @Configuration
    @EnableConfigurationProperties(PssQueueProperties.class)
    static class TestPssQueueProperties {
    }
}



