package uk.nhs.adaptors.pss.translator.task.scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_TRANSLATED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_TIMEOUT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.SDSService;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext
@AutoConfigureMockMvc
public class EHRTimeoutHandlerIT {

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Autowired
    private EHRTimeoutHandler ehrTimeoutHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SDSService sdsService;

    @MockBean
    private SendNACKMessageHandler nackMessageHandler;

    @Test
    public void When_CheckForTimeouts_WithEHRExtractTranslatedAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(EHR_EXTRACT_TRANSLATED);
    }

    @Test
    public void When_CheckForTimeouts_WithContinueRequestAcceptedAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(CONTINUE_REQUEST_ACCEPTED);
    }

    @Test
    public void When_CheckForTimeouts_WithCopcReceivedAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(COPC_MESSAGE_RECEIVED);
    }

    @Test
    public void When_CheckForTimeouts_WithCopcProcessingAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(COPC_MESSAGE_PROCESSING);
    }

    @Test
    public void When_CheckForTimeouts_WithCopcAcknowledgedAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(COPC_ACKNOWLEDGED);
    }

    @Test
    public void When_CheckForTimeouts_WithEhrExtractProcessingAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(EHR_EXTRACT_PROCESSING);
    }

    @Test
    public void When_CheckForTimeouts_WithCopcFailedAndTimedOut_Expect_MigrationStatusLogUpdated() throws IOException {
        checkDatabaseUpdated(EHR_EXTRACT_PROCESSING);
    }

    private void checkDatabaseUpdated(MigrationStatus migrationStatus) throws IOException {

        String losingOdsCode = "P83007";
        String winningOdsCode = "A0378";
        String nhsNumber = "9446363101";
        String persistDuration = "PT4H";
        String conversationId = UUID.randomUUID().toString();
        InboundMessage inboundMessage = createInboundMessage();

        when(sdsService.getPersistDurationFor(any(), any(), any())).thenReturn(Duration.parse(persistDuration));
        when(nackMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        patientMigrationRequestDao.addNewRequest(nhsNumber, conversationId, losingOdsCode, winningOdsCode);
        migrationStatusLogService.updatePatientMigrationRequestAndAddMigrationStatusLog(
            conversationId,
            "{test bundle}",
            objectMapper.writeValueAsString(inboundMessage),
            migrationStatus, null);

        ehrTimeoutHandler.checkForTimeouts();

        MigrationStatusLog statusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);

        assertEquals(ERROR_LRG_MSG_TIMEOUT, statusLog.getMigrationStatus());
    }

    private InboundMessage createInboundMessage() throws IOException {
        InboundMessage inboundMessage = new InboundMessage();
        inboundMessage.setEbXML(readFileAsString("xml/RCMR_IN030000UK06/ebxml_part.xml"));
        inboundMessage.setPayload(readFileAsString("xml/RCMR_IN030000UK06/payload_part.xml"));
        return inboundMessage;
    }

    private static String readFileAsString(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        return Files.readString(Paths.get(resource.getURI()));
    }
}
