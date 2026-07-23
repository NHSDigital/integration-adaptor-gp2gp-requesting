package uk.nhs.adaptors.pss.gpc.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.gpc.GpcFacadeApplication;

@SpringBootTest(classes = GpcFacadeApplication.class)
@AutoConfigureMockMvc
public class AcknowledgeRecordContractIT {

    private static final String ENDPOINT = "/$gpc.ack";
    private static final String CONTENT_TYPE = "application/fhir+json";
    private static final String PATIENT_NUMBER = "123456789";
    private static final String LOSING_PRACTICE_ODS = "F765";
    private static final String WINNING_PRACTICE_ODS = "B943";

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldAcceptAcknowledgeRequestWhenHeadersAreValid() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        addMigrationRequestAndCompletedStatus(conversationId);

        mockMvc.perform(post(ENDPOINT)
                .header("conversationId", conversationId)
                .header("confirmationResponse", "ACCEPTED"))
            .andExpect(status().isOk());
    }

    @Test
    public void shouldReturnOperationOutcomeWhenConversationIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .header("confirmationResponse", "ACCEPTED"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(CONTENT_TYPE))
            .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
            .andExpect(jsonPath("$.issue[0].code").value("invalid"))
            .andExpect(jsonPath("$.issue[0].details.coding[0].code").value("BAD_REQUEST"));
    }

    private void addMigrationRequestAndCompletedStatus(String conversationId) {
        var upperConversationId = conversationId.toUpperCase();
        patientMigrationRequestDao.addNewRequest(PATIENT_NUMBER, upperConversationId, LOSING_PRACTICE_ODS, WINNING_PRACTICE_ODS);
        patientMigrationRequestDao.saveBundleAndInboundMessageData(upperConversationId, "{bundle}", "{message}");
        migrationStatusLogService.addMigrationStatusLog(MIGRATION_COMPLETED, upperConversationId, null, null);
    }
}

