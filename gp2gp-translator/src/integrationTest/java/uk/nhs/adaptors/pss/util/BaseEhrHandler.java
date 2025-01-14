package uk.nhs.adaptors.pss.util;

import static org.assertj.core.api.Assertions.fail;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_TRANSLATED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.pss.util.JsonPathIgnoreGeneratorUtil.generateJsonPathIgnores;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;

import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.web.client.RestTemplate;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.mhsmock.model.Request;
import uk.nhs.adaptors.pss.mhsmock.model.RequestJournal;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;

@Getter
public abstract class BaseEhrHandler {
    public static final boolean OVERWRITE_EXPECTED_JSON = false;
    private static final String REQUEST_JOURNAL_PATH = "/__admin/requests";
    private static final String ACK_INTERACTION_ID = "MCCI_IN010000UK13";
    private static final String NACK_PAYLOAD_PATH = "/xml/MCCI_IN010000UK13/payload_part.xml";
    private static final String NACK_EBXML_PATH = "/xml/MCCI_IN010000UK13/ebxml_part.xml";
    private static final String NACK_TYPE_CODE_PLACEHOLDER = "{{typeCode}}";
    private static final String NACK_TYPE_CODE = "AE";
    private final RestTemplate restTemplate = new RestTemplate();

    private List<String> ignoredJsonPaths;
    private static final int NHS_NUMBER_MIN_MAX_LENGTH = 10;

    @Getter
    protected static final String NHS_NUMBER_PLACEHOLDER = "{{nhsNumber}}";

    @Getter
    protected static final String CONVERSATION_ID_PLACEHOLDER = "{{conversationId}}";

    @Getter @Setter
    private String losingODSCode = "D5445";
    @Getter @Setter
    private String winingODSCode = "ABC";
    @Getter @Setter
    private String patientNhsNumber;
    @Getter @Setter
    private String conversationId;

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Autowired
    private FhirParser fhirParserService;

    @Qualifier("jmsTemplateMhsQueue")
    @Autowired
    private JmsTemplate mhsJmsTemplate;

    @Qualifier("jmsTemplatePssQueue")
    @Autowired
    private JmsTemplate pssJmsTemplate;
    @Value("${mhs.url}")
    private String mhsMockHost;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        patientNhsNumber = generatePatientNhsNumber();
        conversationId = generateConversationId().toUpperCase(Locale.ROOT);
        startPatientMigrationJourney();
    }

    protected void startPatientMigrationJourney() {
        patientMigrationRequestDao.addNewRequest(patientNhsNumber, conversationId, losingODSCode, winingODSCode);
        migrationStatusLogService.addMigrationStatusLog(EHR_EXTRACT_REQUEST_ACCEPTED, conversationId, null, null);
    }

    protected boolean isEhrExtractTranslated() {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);
        return EHR_EXTRACT_TRANSLATED.equals(migrationStatusLog.getMigrationStatus());
    }

    protected boolean isEhrMigrationCompleted() {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);
        return MIGRATION_COMPLETED.equals(migrationStatusLog.getMigrationStatus());
    }

    protected boolean isMigrationStatus(MigrationStatus migrationStatus) {
        var migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);
        return migrationStatus.equals(migrationStatusLog.getMigrationStatus());
    }

    protected void verifyBundle(String path) throws JSONException {
        var patientMigrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        var expectedBundle = readResourceAsString(path).replace(NHS_NUMBER_PLACEHOLDER, patientNhsNumber);

        if (OVERWRITE_EXPECTED_JSON) {
            overwriteExpectJson(path, patientMigrationRequest.getBundleResource());
        }

        var bundle = fhirParserService.parseResource(patientMigrationRequest.getBundleResource(), Bundle.class);
        var combinedList = Stream.of(generateJsonPathIgnores(bundle), ignoredJsonPaths)
            .flatMap(List::stream)
            .toList();

        assertBundleContent(patientMigrationRequest.getBundleResource(), expectedBundle, combinedList);
    }

    @SneakyThrows
    protected void overwriteExpectJson(String path, String newExpected) {
        try (PrintWriter printWriter = new PrintWriter("src/integrationTest/resources/" + path, StandardCharsets.UTF_8)) {
            printWriter.print(newExpected);
        }
        fail("Re-run the tests with OVERWRITE_EXPECTED_JSON=false");
    }

    protected void assertBundleContent(String actual, String expected, List<String> ignoredPaths) throws JSONException {
        // when comparing json objects, this will ignore various json paths that contain random values like ids or timestamps
        var customizations = ignoredPaths.stream()
            .map(jsonPath -> new Customization(jsonPath, (o1, o2) -> true))
            .toArray(Customization[]::new);

        JSONAssert.assertEquals(expected, actual,
            new CustomComparator(JSONCompareMode.STRICT, customizations));
    }

    protected String generatePatientNhsNumber() {
        return RandomStringUtils.randomNumeric(NHS_NUMBER_MIN_MAX_LENGTH, NHS_NUMBER_MIN_MAX_LENGTH);
    }

    protected void setIgnoredJsonPaths(List<String> ignoredJsonPaths) {
        this.ignoredJsonPaths = ignoredJsonPaths;
    }

    protected String generateConversationId() {
        return UUID.randomUUID().toString();
    }

    protected void sendInboundMessageToQueue(String json) {
        getMhsJmsTemplate().send(
            session -> session.createTextMessage(
                fetchMessageInJsonFormat(json)
            )
        );
    }

    @NotNull
    protected String fetchMessageInJsonFormat(String json) {
        return readResourceAsString(json)
            .replace(NHS_NUMBER_PLACEHOLDER, getPatientNhsNumber())
            .replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());
    }

    protected boolean hasNackBeenSentWithCode(String code) {
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
                .contains("<code code=\\\"" + code + "\\\" codeSystem=\\\"2.16.840.1.113883.2.1.3.2.4.17.101\\\"");
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

    protected void sendNackToQueue() {
        var inboundMessage = createNackMessage();
        mhsJmsTemplate.send(session -> session.createTextMessage(parseMessageToString(inboundMessage)));
    }

    private InboundMessage createNackMessage() {
        var inboundMessage = new InboundMessage();
        var payload = readResourceAsString(NACK_PAYLOAD_PATH).replace(NACK_TYPE_CODE_PLACEHOLDER, NACK_TYPE_CODE);
        var ebxml = readResourceAsString(NACK_EBXML_PATH).replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());
        inboundMessage.setPayload(payload);
        inboundMessage.setEbXML(ebxml);
        return inboundMessage;
    }

    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }
}
