package uk.nhs.adaptors.pss.translator;

import static org.awaitility.Awaitility.await;

import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext
public class E2EMappingIT extends BaseEhrHandler {
    private static final String EBXML_PART_PATH = "/xml/RCMR_IN030000UK06/ebxml_part.xml";
    public static final String INPUT_RESOURCE_PATH = "/e2e-mapping/input-xml/";
    public static final String OUTPUT_RESOURCE_PATH = "/e2e-mapping/output-json/";

    @Autowired
    private ObjectMapper objectMapper;

    @Qualifier("jmsTemplateMhsQueue")
    @Autowired
    private JmsTemplate mhsJmsTemplate;

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @MockBean
    private IdGeneratorService idGeneratorService;

    @BeforeEach
    public void setUpDeterministicRandomIds() {
        when(idGeneratorService.generateUuid()).thenAnswer(
                new Answer<String>() {
                    private int invocationCount = 0;
                    @Override
                    public String answer(InvocationOnMock invocation) {
                        return String.format("00000000-0000-0000-0000-%012d", invocationCount++);
                    }
                }
        );
    }

    @Override
    @BeforeEach
    public void setUp() {
        setPatientNhsNumber(generatePatientNhsNumber());
        setConversationId(generateConversationId().toUpperCase());
        setLosingODSCode("B83002");
        setWiningODSCode("C81007");
        startPatientMigrationJourney();
    }

    @Test
    public void handlePWTP2EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP2-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP2.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP3EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP3-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP3.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP4EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP4-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP4.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP5EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP5-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP5.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP6EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP6-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP6.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP7visEhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP7_vis-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP7_vis.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP9EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP9-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP9.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP10EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP10-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP10.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    @Test
    public void handlePWTP11EhrExtractFromQueue() throws JSONException {
        final var expectedBundle = readResourceAsString(OUTPUT_RESOURCE_PATH + "PWTP11-output.json");

        final var actualBundle = sendInboundMessageAndWaitForBundle(INPUT_RESOURCE_PATH + "PWTP11.xml");

        JSONAssert.assertEquals(expectedBundle, actualBundle, true);
    }

    private String sendInboundMessageAndWaitForBundle(String inputFilePath) {
        final var inboundMessage = parseMessageToString(createInboundMessage(inputFilePath));
        mhsJmsTemplate.send(session -> session.createTextMessage(inboundMessage));
        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.MIGRATION_COMPLETED));

        var patientMigrationRequest = patientMigrationRequestDao.getMigrationRequest(getConversationId());
        return patientMigrationRequest.getBundleResource();
    }

    private InboundMessage createInboundMessage(String payloadPartPath) {
        var ebXml = readResourceAsString(EBXML_PART_PATH).replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readResourceAsString(payloadPartPath));
        inboundMessage.setEbXML(ebXml);
        return inboundMessage;
    }

    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }
}