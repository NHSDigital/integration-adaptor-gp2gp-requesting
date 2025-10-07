package uk.nhs.adaptors.pss.translator.amqp;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.task.SendContinueRequestHandler;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class SendContinueHandlerFailureIT extends BaseEhrHandler {
    public static final String JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON = "/json/LargeMessage/Scenario_3/uk06.json";

    @TestConfiguration
    static class MockedBeansConfig {
        @Bean
        public SendContinueRequestHandler sendContinueRequestHandler() {
            return Mockito.mock(SendContinueRequestHandler.class);
        }
    }

    @Autowired
    private SendContinueRequestHandler sendContinueRequestHandler;

    @Test
    public void When_ReceivingEhrExtract_WithMhsOutboundServerError_Expect_MigrationHasProcessingError() {
        doThrow(MhsServerErrorException.class)
            .when(sendContinueRequestHandler).prepareAndSendRequest(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(() -> hasMigrationStatus(EHR_GENERAL_PROCESSING_ERROR, getConversationId()));

        verify(sendContinueRequestHandler, times(1)).prepareAndSendRequest(any());

        assertThat(getCurrentMigrationStatus(getConversationId()))
            .isEqualTo(EHR_GENERAL_PROCESSING_ERROR);
    }
}
