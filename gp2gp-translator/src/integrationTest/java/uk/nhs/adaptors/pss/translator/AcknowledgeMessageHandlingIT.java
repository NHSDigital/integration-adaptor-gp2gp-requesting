package uk.nhs.adaptors.pss.translator;

import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;

import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext
@AutoConfigureMockMvc
public class AcknowledgeMessageHandlingIT {

    private static final int NHS_NUMBER_MIN_MAX_LENGTH = 10;
    private static final String EBXML_PART_PATH = "/xml/MCCI_IN010000UK13/ebxml_part.xml";
    private static final String PAYLOAD_PART_PATH = "/xml/MCCI_IN010000UK13/payload_part.xml";
    private static final String PAYLOAD_PART_PATH_WITH_ERROR_REASON = "/xml/MCCI_IN010000UK13/payload_part_with_reason.xml";
    private static final String TYPE_CODE_PLACEHOLDER = "{{typeCode}}";
    private static final String CONVERSATION_ID_PLACEHOLDER = "{{conversationId}}";
    private static final String ERROR_REASON_CODE_PLACEHOLDER = "{{reasonCode}}";
    private static final String ERROR_REASON_MESSAGE_PLACEHOLDER = "{{reasonMessage}}";
    private static final String LOSING_ODS_CODE = "K547";
    private static final String WINNING_ODS_CODE = "ABC";
    public static final String TEST_ERROR_MESSAGE = "Test Error Message";

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Qualifier("jmsTemplateMhsQueue")
    @Autowired
    private JmsTemplate mhsJmsTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String conversationId;

    @BeforeEach
    public void setUp() {
        conversationId = generateConversationId().toUpperCase(Locale.ROOT);
        patientMigrationRequestDao.addNewRequest(generatePatientNhsNumber(), conversationId, LOSING_ODS_CODE, WINNING_ODS_CODE);
        migrationStatusLogService.addMigrationStatusLog(EHR_EXTRACT_REQUEST_ACCEPTED, conversationId, null, null);
    }

    @Test
    public void handlePositiveAcknowledgeMessageFromQueue() {
        sendAcknowledgementMessageToQueue("AA", null, null);
        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_ACKNOWLEDGED));
    }

    @Test
    public void handleNegativeAcknowledgeMessageFromQueue() {
        sendAcknowledgementMessageToQueue("AE", null, null);
        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithUndeclairedErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "101", TEST_ERROR_MESSAGE);
        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithUnknownErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "99", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithPatientNotRegisteredErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "06", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithSENDERNOTCONFIGUREDErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "07", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithEHRGENERATIONErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "10", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithMISFORMEDREQUESTErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "18", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithNOTPRIMARYHEALTHCAREPROVIDERErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "19", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER));
    }

    @Test
    public void handleNegativeAcknowledgeMessageWithMULTIORNORESPONSESErrorReasonFromQueue() {
        sendAcknowledgementMessageToQueue("AE", "24", TEST_ERROR_MESSAGE);

        // verify if correct status is set in the DB
        await().until(() -> isCorrectStatusSet(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES));
    }

    private String generatePatientNhsNumber() {
        return RandomStringUtils.randomNumeric(NHS_NUMBER_MIN_MAX_LENGTH, NHS_NUMBER_MIN_MAX_LENGTH);
    }

    private String generateConversationId() {
        return UUID.randomUUID().toString();
    }

    private void sendAcknowledgementMessageToQueue(String typeCode, String reasonCode, String reasonMessage) {

        var inboundMessage = createInboundMessage(typeCode, reasonCode, reasonMessage);
        mhsJmsTemplate.send(session -> session.createTextMessage(parseMessageToString(inboundMessage)));
    }

    private InboundMessage createInboundMessage(String typeCode, String reasonCode, String reasonMessage) {
        var inboundMessage = new InboundMessage();

        String payload = null;
        if (reasonCode == null || reasonCode.isEmpty()) {
            payload = readResourceAsString(PAYLOAD_PART_PATH);
        } else {
            payload = readResourceAsString(PAYLOAD_PART_PATH_WITH_ERROR_REASON);
            payload = payload.replace(ERROR_REASON_CODE_PLACEHOLDER, reasonCode);
            payload = payload.replace(ERROR_REASON_MESSAGE_PLACEHOLDER, reasonMessage);
        }

        payload = payload.replace(TYPE_CODE_PLACEHOLDER, typeCode);
        var ebXml = readResourceAsString(EBXML_PART_PATH).replace(CONVERSATION_ID_PLACEHOLDER, conversationId);
        inboundMessage.setPayload(payload);
        inboundMessage.setEbXML(ebXml);
        return inboundMessage;
    }

    private boolean isCorrectStatusSet(MigrationStatus expectedStatus) {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);
        return migrationStatusLog != null && expectedStatus.equals(migrationStatusLog.getMigrationStatus());
    }

    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }
}
