package uk.nhs.adaptors.pss.translator;

import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

import java.time.Duration;
import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext
@AutoConfigureMockMvc
public class EhrExtractHandlingIT extends BaseEhrHandler  {

    private static final String EBXML_PART_PATH = "/xml/RCMR_IN030000UK06/ebxml_part.xml";

    private static final List<String> STATIC_IGNORED_JSON_PATHS = List.of(
        "id",
        "entry[0].resource.id",
        "entry[0].resource.identifier[0].value",
        "entry[1].resource.id",
        "entry[*].resource.subject.reference",
        "entry[*].resource.patient.reference",
        "entry[21].resource.location[0].location.reference",
        "entry[22].resource.location[0].location.reference",
        "entry[23].resource.location[0].location.reference",
        "entry[24].resource.location[0].location.reference",
        "entry[25].resource.location[0].location.reference",
        "entry[26].resource.location[0].location.reference",
        "entry[29].resource.location[0].location.reference",
        "entry[31].resource.location[0].location.reference",
        "entry[59].resource.id",
        "entry[59].resource.identifier[0].value",
        "entry[60].resource.id",
        "entry[60].resource.identifier[0].value",
        "entry[502].resource.id",
        "entry[502].resource.identifier[0].value",
        "entry[504].resource.id",
        "entry[504].resource.identifier[0].value"
    );

    @MockBean
    private IdGeneratorService idGeneratorService;

    @Qualifier("jmsTemplateMhsQueue")
    @Autowired
    private JmsTemplate mhsJmsTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static final int WAITING_TIME = 20;

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

    @BeforeEach
    public void setUpIgnoredJsonPaths() {
        setIgnoredJsonPaths(STATIC_IGNORED_JSON_PATHS);
    }

    @Test
    public void handleEhrExtractFromQueue() throws JSONException {
        // process starts with consuming a message from MHS queue
        sendInboundMessageToQueue("/xml/RCMR_IN030000UK06/payload_part.xml", EBXML_PART_PATH);

        // wait until EHR extract is translated to bundle resource and saved to the DB
        waitAtMost(Duration.ofSeconds(WAITING_TIME)).until(this::isEhrMigrationCompleted);

        // verify generated bundle resource
        verifyBundle("/json/expectedBundle.json");
    }

    private void sendInboundMessageToQueue(String payloadPartPath, String ebxmlPartPath) {
        var inboundMessage = createInboundMessage(payloadPartPath, ebxmlPartPath);
        mhsJmsTemplate.send(session -> session.createTextMessage(parseMessageToString(inboundMessage)));
    }

    private InboundMessage createInboundMessage(String payloadPartPath, String ebxmlPartPath) {
        var inboundMessage = new InboundMessage();
        var payload = readResourceAsString(payloadPartPath).replace(NHS_NUMBER_PLACEHOLDER, getPatientNhsNumber());
        var ebXml = readResourceAsString(ebxmlPartPath).replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());
        inboundMessage.setPayload(payload);
        inboundMessage.setEbXML(ebXml);
        return inboundMessage;
    }

    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }
}
