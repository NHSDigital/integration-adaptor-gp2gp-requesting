package uk.nhs.adaptors.pss.translator.amqp;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_GENERAL_FAILURE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.REQUEST_RECEIVED;
import static uk.nhs.adaptors.common.enums.QueueMessageType.TRANSFER_REQUEST;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import ca.uhn.fhir.parser.DataFormatException;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.jdbi.v3.core.ConnectionException;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.common.model.TransferRequestMessage;
import uk.nhs.adaptors.pss.translator.config.PssQueueProperties;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.service.MhsClientService;
import uk.nhs.adaptors.pss.translator.task.SendACKMessageHandler;
import uk.nhs.adaptors.pss.translator.task.SendContinueRequestHandler;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;
import jakarta.jms.JMSException;
import jakarta.jms.Message;


@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ServiceFailureIT extends BaseEhrHandler {

    private static final String LOSING_ASID = "LOSING_ASID";
    private static final String WINNING_ASID = "WINNING_ASID";
    private static final String STUB_BODY = "test Body";
    private static final int THIRTY_SECONDS = 30000;
    private static final long TWO_MINUTES_LONG = 2L;
    private static final int FIVE_WANTED_NUMBER_OF_INVOCATIONS = 5;
    public static final String JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON = "/json/LargeMessage/Scenario_3/uk06.json";
    public static final String JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON = "/json/LargeMessage/Scenario_3/copc.json";
    public static final String JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON = "/json/LargeMessage/expectedBundleScenario3.json";
    public static final int RECEIVE_TIMEOUT_LIMIT = 50;
    private String conversationId;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PssQueueProperties pssQueueProperties;

    @Autowired
    @Qualifier("jmsTemplateMhsDLQ")
    private JmsTemplate dlqJmsTemplate;

    @Mock
    private HttpHeaders httpHeaders;

    @MockitoSpyBean
    private MhsClientService mhsClientService;
    @MockitoSpyBean
    private SendContinueRequestHandler sendContinueRequestHandler;
    @MockitoSpyBean
    private SendACKMessageHandler sendACKMessageHandler;
    @MockitoSpyBean
    private SendNACKMessageHandler sendNACKMessageHandler;
    @MockitoSpyBean
    private MhsDlqPublisher mhsDlqPublisher;

    @BeforeEach
    public void setupIgnoredPaths() {
        setIgnoredJsonPaths(List.of(
            "id",
            "entry[0].resource.id",
            "entry[0].resource.identifier[0].value",
            "entry[*].resource.id",
            "entry[*].resource.subject.reference",
            "entry[*].resource.patient.reference",
            "entry[*].resource.performer[0].reference",
            "entry[*].resource.content[0].attachment.title",
            "entry[*].resource.content[0].attachment.url",
            "entry[*].resource.description"
        ));
        conversationId = generateConversationId().toUpperCase(Locale.ROOT);
    }

    @Test
    public void When_SendingInitialRequest_WithMhsOutboundServerError_Expect_MigrationLogHasRequestError() {

        var patientNhsNumber = generatePatientNhsNumber();

        doThrow(getInternalServerErrorException())
            .when(mhsClientService).send(any());

        sendRequestToPssQueue(conversationId, patientNhsNumber);

        await().until(() -> hasMigrationStatus(EHR_EXTRACT_REQUEST_ERROR, conversationId));

        verify(mhsClientService, timeout(THIRTY_SECONDS).times(pssQueueProperties.getMaxRedeliveries() + 1)
        ).send(any());

        assertThat(getCurrentMigrationStatus(conversationId))
            .isEqualTo(EHR_EXTRACT_REQUEST_ERROR);
    }

    @Test
    public void When_ReceivingCOPC_WithDataFormatExceptionError_Expect_MessageSentToDLQ() {
        doThrow(DataFormatException.class)
            .when(sendACKMessageHandler).prepareAndSendMessage(any());

        doThrow(DataFormatException.class)
            .when(sendNACKMessageHandler).prepareAndSendMessage(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().until(() -> hasMigrationStatus(ERROR_LRG_MSG_GENERAL_FAILURE, getConversationId()));

        verify(mhsDlqPublisher, timeout(THIRTY_SECONDS).times(1)).sendToMhsDlq(any());

        assertThat(getCurrentMigrationStatus(getConversationId()))
            .isEqualTo(ERROR_LRG_MSG_GENERAL_FAILURE);
    }

    @Test
    public void When_ReceivingCOPC_WithMhsServerErrorException_Expect_MessageSentToDLQ() throws JMSException {

        dlqCleanUp();

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        doThrow(MhsServerErrorException.class).when(mhsClientService).send(any());

        var copcMessageInJsonFormat = fetchMessageInJsonFormat(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);
        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        dlqJmsTemplate.setReceiveTimeout(THIRTY_SECONDS);
        Message messageSentToDlq = dlqJmsTemplate.receive();

        assertNotNull(messageSentToDlq);
        assertEquals(copcMessageInJsonFormat, ((JmsTextMessage) messageSentToDlq).getText());
        verify(mhsClientService, times(FIVE_WANTED_NUMBER_OF_INVOCATIONS)).send(any());
    }

    @Test
    public void When_SendingInitialRequest_WithMhsWebClientRequestException_Expect_MigrationCompletesWhenMhsRecovers()
        throws JSONException {

        var patientNhsNumber = generatePatientNhsNumber();

        doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doCallRealMethod()
            .when(mhsClientService).send(any());

        sendRequestToPssQueue(conversationId, patientNhsNumber);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .until(() -> hasMigrationStatus(EHR_EXTRACT_REQUEST_ACCEPTED, conversationId));

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_ReceivingEhrExtract_WithMhsWebClientRequestException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {
        doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doThrow(WebClientRequestException.class)
            .doCallRealMethod()
            .when(mhsClientService).send(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_ReceivingCopc_WithMhsWebClientRequestException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        doThrow(WebClientRequestException.class)
        .doThrow(WebClientRequestException.class)
        .doThrow(WebClientRequestException.class)
        .doThrow(WebClientRequestException.class)
        .doThrow(WebClientRequestException.class)
        .doCallRealMethod()
        .when(mhsClientService).send(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_ReceivingCopc_WithMhsWebClientResponseException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG)).until(this::hasContinueMessageBeenReceived);

        var webClientResponseException = getInternalServerErrorException();

        doThrow(webClientResponseException)
            .doThrow(webClientResponseException)
            .doThrow(webClientResponseException)
        .doCallRealMethod()
        .when(mhsClientService).send(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_SendingInitialRequest_WithDBConnectionException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {

        var patientNhsNumber = generatePatientNhsNumber();

        doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doCallRealMethod()
            .when(mhsClientService).send(any());

        sendRequestToPssQueue(conversationId, patientNhsNumber);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .until(() -> hasMigrationStatus(EHR_EXTRACT_REQUEST_ACCEPTED, conversationId));

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_ReceivingEhrExtract_WithDbConnectionException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {
        doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doCallRealMethod()
            .when(mhsClientService).send(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    @Test
    public void When_ReceivingCopc_WithDbConnectionException_Expect_MigrationCompletesWhenMhsRecovers() throws JSONException {

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_UK_06_JSON);

        await().until(this::hasContinueMessageBeenReceived);

        doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doThrow(ConnectionException.class)
            .doCallRealMethod()
            .when(mhsClientService).send(any());

        sendInboundMessageToQueue(JSON_LARGE_MESSAGE_SCENARIO_3_COPC_JSON);

        await().atMost(Duration.ofMinutes(TWO_MINUTES_LONG))
            .untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        verifyBundle(JSON_LARGE_MESSAGE_EXPECTED_BUNDLE_SCENARIO_3_JSON);
    }

    private void dlqCleanUp() {
        dlqJmsTemplate.setReceiveTimeout(RECEIVE_TIMEOUT_LIMIT);
        long timeToLive;
        while (dlqJmsTemplate.receive() != null) {
            timeToLive = dlqJmsTemplate.getTimeToLive();
        }
    }

    private void sendRequestToPssQueue(String conversationId, String patientNhsNumber) {

        getPatientMigrationRequestDao()
            .addNewRequest(patientNhsNumber, conversationId, getLosingODSCode(), getWiningODSCode());
        getMigrationStatusLogService()
            .addMigrationStatusLog(REQUEST_RECEIVED, conversationId, null, null);

        var transferRequestMessage = TransferRequestMessage.builder()
            .patientNhsNumber(patientNhsNumber)
            .toOds(getLosingODSCode())
            .fromOds(getWiningODSCode())
            .fromAsid(WINNING_ASID)
            .toAsid(LOSING_ASID)
            .messageType(TRANSFER_REQUEST)
            .conversationId(conversationId)
            .build();

        getPssJmsTemplate().send(session ->
            session.createTextMessage(getObjectAsString(transferRequestMessage)));
    }

    @SneakyThrows
    private String getObjectAsString(Object object) {
        return objectMapper.writeValueAsString(object);
    }

    private WebClientResponseException getInternalServerErrorException() {
        return new WebClientResponseException(
            INTERNAL_SERVER_ERROR.value(), INTERNAL_SERVER_ERROR.getReasonPhrase(), httpHeaders, STUB_BODY.getBytes(UTF_8), UTF_8);
    }

    private boolean hasContinueMessageBeenReceived() {
        var migrationStatusLog = getMigrationStatusLogService().getLatestMigrationStatusLog(getConversationId());
        return CONTINUE_REQUEST_ACCEPTED.equals(migrationStatusLog.getMigrationStatus());
    }
}
