package uk.nhs.adaptors.pss.translator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_TIMEOUT;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.mhsmock.model.Request;
import uk.nhs.adaptors.pss.mhsmock.model.RequestJournal;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

@SpringBootTest
@ExtendWith({SpringExtension.class})
@DirtiesContext
public class FailedProcessHandingIT extends BaseEhrHandler {

    private static final String NACK_PAYLOAD_PATH = "/xml/MCCI_IN010000UK13/payload_part.xml";
    private static final String NACK_EBXML_PATH = "/xml/MCCI_IN010000UK13/ebxml_part.xml";
    private static final String EHR_MESSAGE_EXTRACT_PATH = "/json/LargeMessage/Scenario_3/uk06.json";
    private static final String COPC_MESSAGE_PATH = "/json/LargeMessage/Scenario_3/copc.json";
    private static final String NACK_TYPE_CODE_PLACEHOLDER = "{{typeCode}}";
    private static final String CONVERSATION_ID_PLACEHOLDER = "{{conversationId}}";
    private static final String NACK_TYPE_CODE = "AE";

    private static final String REQUEST_JOURNAL_PATH = "/__admin/requests";
    private static final String ACK_INTERACTION_ID = "MCCI_IN010000UK13";

    private static final String UNEXPECTED_CONDITION_CODE = "99";
    private static final String LARGE_MESSAGE_TIMEOUT_CODE = "25";
    private static final String EHR_GENERAL_PROCESSING_ERROR_CODE = "30";
    private static final String EHR_NACK_UNKNOWN = "99";

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Qualifier("jmsTemplateMhsQueue")
    @Autowired
    private JmsTemplate mhsJmsTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mhs.url}")
    private String mhsMockHost;

    @Test
    public void When_ProcessFailedByIncumbent_With_EhrExtract_Expect_NotProcessed() {
        sendNackToQueue();

        await().until(this::isEhrRequestNegativeAckUnknown);

        sendEhrExtractToQueue();

        await().until(() -> nackSentWithCode(UNEXPECTED_CONDITION_CODE));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN);
    }

    @Test
    public void When_ProcessFailedByIncumbent_With_CopcMessage_Expect_NotProcessed() {
        sendEhrExtractToQueue();

        await().until(this::isContinueRequestAccepted);

        sendNackToQueue();

        await().until(this::isEhrRequestNegativeAckUnknown);

        sendCopcToQueue();

        await().until(() -> nackSentWithCode(EHR_NACK_UNKNOWN));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN);
    }

    @Test
    public void When_ProcessFailedByNME_With_EhrExtract_Expect_NotProcessed() {
        migrationStatusLogService.addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR, getConversationId(), null, "99");

        sendEhrExtractToQueue();

        await().until(() -> nackSentWithCode(UNEXPECTED_CONDITION_CODE));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_GENERAL_PROCESSING_ERROR);
    }

    @Test
    public void When_ProcessFailedByNme_With_CopcMessageAndTimeout_Expect_NotProcessed() {
        whenCopcSentExpectNackCode(ERROR_LRG_MSG_TIMEOUT, LARGE_MESSAGE_TIMEOUT_CODE);
    }

    @Test
    public void When_ProcessFailedByNme_With_CopcMessage_Expect_NotProcessed() {
        whenCopcSentExpectNackCode(EHR_GENERAL_PROCESSING_ERROR, EHR_GENERAL_PROCESSING_ERROR_CODE);
    }

    private void whenCopcSentExpectNackCode(MigrationStatus preCopcMigrationStatus, String nackCode) {
        sendEhrExtractToQueue();

        await().until(this::isContinueRequestAccepted);

        migrationStatusLogService.addMigrationStatusLog(preCopcMigrationStatus, getConversationId(), null, null);

        sendCopcToQueue();

        await().until(() -> nackSentWithCode(nackCode));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(preCopcMigrationStatus);
    }

    private void sendNackToQueue() {
        var inboundMessage = createNackMessage();
        mhsJmsTemplate.send(session -> session.createTextMessage(parseMessageToString(inboundMessage)));
    }

    private void sendEhrExtractToQueue() {
        sendInboundMessageToQueue(EHR_MESSAGE_EXTRACT_PATH);
    }

    private void sendCopcToQueue() {
        sendInboundMessageToQueue(COPC_MESSAGE_PATH);
    }

    private InboundMessage createNackMessage() {
        var inboundMessage = new InboundMessage();
        var payload = readResourceAsString(NACK_PAYLOAD_PATH).replace(NACK_TYPE_CODE_PLACEHOLDER, NACK_TYPE_CODE);
        var ebxml = readResourceAsString(NACK_EBXML_PATH).replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());
        inboundMessage.setPayload(payload);
        inboundMessage.setEbXML(ebxml);
        return inboundMessage;
    }

    private void sendInboundMessageToQueue(String json) {
        var jsonMessage = readResourceAsString(json)
            .replace(NHS_NUMBER_PLACEHOLDER, getPatientNhsNumber())
            .replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());
        getMhsJmsTemplate().send(session -> session.createTextMessage(jsonMessage));
    }


    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }

    private boolean isEhrRequestNegativeAckUnknown() {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId());
        return migrationStatusLog != null && EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN.equals(migrationStatusLog.getMigrationStatus());
    }

    private boolean isContinueRequestAccepted() {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId());
        return migrationStatusLog != null && CONTINUE_REQUEST_ACCEPTED.equals(migrationStatusLog.getMigrationStatus());
    }

    private boolean nackSentWithCode(String code) {
        ArrayList<Request> requests = new ArrayList<>(getMhsRequestsForConversation());

        if (requests.isEmpty()) {
            return false;
        }

        requests.sort(Comparator.comparing(Request::getLoggedDate).reversed());

        var mostRecentRequest = requests.getFirst();

        return mostRecentRequest.getHeaders().getInteractionId().equals(ACK_INTERACTION_ID)
            && mostRecentRequest.getBody()
                .contains("<acknowledgement typeCode=\\\"AE\\\">")
            && mostRecentRequest.getBody()
                .contains("<code code=\\\"" + code + "\\\" codeSystem=\\\"2.16.840.1.113883.2.1.3.2.4.17.101\\\">");
    }

    private List<Request> getMhsRequestsForConversation() {
        var requestJournal = restTemplate.getForObject(mhsMockHost + REQUEST_JOURNAL_PATH, RequestJournal.class);

        if (requestJournal == null) {
            return Collections.emptyList();
        }

        return requestJournal.getRequests().stream()
            .map(RequestJournal.RequestEntry::getRequest)
            .filter(request -> request.getHeaders().getCorrelationId() != null
                && request.getHeaders().getCorrelationId().equals(getConversationId()))
            .toList();
    }
}
