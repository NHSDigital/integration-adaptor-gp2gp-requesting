package uk.nhs.adaptors.pss.gpc.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.adaptors.common.enums.ConfirmationResponse;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.gpc.GpcFacadeApplication;

import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;

@SpringBootTest(classes = GpcFacadeApplication.class)
@AutoConfigureMockMvc
public class AcknowledgeRecordControllerIT {
    private static final String ACKNOWLEDGE_RECORD_ENDPOINT = "/$gpc.ack";
    private static final String CONVERSATION_ID_HEADER = "conversationId";
    private static final String CONFIRMATION_RESPONSE_HEADER = "confirmationResponse";
    private static final String PATIENT_NUMBER = "123456789";
    private static final String LOSING_PRACTICE_ODS = "F765";
    private static final String WINNING_PRACTICE_ODS = "B943";
    private static final String BUNDLE_VALUE = "{bundle}";
    private static final String INBOUND_MESSAGE_VALUE = "{message}";

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void sendNewAcknowledgeRecordRequestAccepted() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        addMigrationRequestAndLogWithStatus(conversationId, MIGRATION_COMPLETED);

        mockMvc.perform(
                        post(ACKNOWLEDGE_RECORD_ENDPOINT)
                                .header(CONVERSATION_ID_HEADER, conversationId)
                                .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isOk());
    }

    @Test
    public void sendNewAcknowledgeRecordRequestNotAccepted() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        addMigrationRequestAndLogWithStatus(conversationId, MIGRATION_COMPLETED);

        mockMvc.perform(
                        post(ACKNOWLEDGE_RECORD_ENDPOINT)
                                .header(CONVERSATION_ID_HEADER, conversationId)
                                .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = MigrationStatus.class, names = {"MIGRATION_COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    public void sendNewAcknowledgeRecordRequestWhenMigrationIsNotCompleted(MigrationStatus status) throws Exception {
        var conversationId = UUID.randomUUID().toString();
        addMigrationRequestAndLogWithStatus(conversationId, status);

        mockMvc.perform(
                        post(ACKNOWLEDGE_RECORD_ENDPOINT)
                                .header(CONVERSATION_ID_HEADER, conversationId)
                                .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void sendAcknowledgeRecordRequestToInvalidEndpoint() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        var invalidEndpoint = "/$gpc.notAnEndpoint";
        var expectedResponseBody = readResourceAsString("/responses/common/notFoundResponseBody.json")
                .replace("{{endpointUrl}}", invalidEndpoint);

        mockMvc.perform(
                        post(invalidEndpoint)
                                .header(CONVERSATION_ID_HEADER, conversationId)
                                .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isNotFound())
                .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendAcknowledgeRequestUsingIncorrectMethod() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        var expectedResponseBody = readResourceAsString("/responses/common/methodNotAllowedResponseBody.json")
                .replace("{{requestMethod}}", "PATCH");

        mockMvc.perform(patch(ACKNOWLEDGE_RECORD_ENDPOINT)
                        .header(CONVERSATION_ID_HEADER, conversationId)
                        .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendAcknowledgeRequestWithMissingConversationIdHeader() throws Exception {
        var expectedResponseBody = readResourceAsString("/responses/acknowledge-record/badRequestResponseBody.json")
                .replace("{{header}}", CONVERSATION_ID_HEADER);

        mockMvc.perform(post(ACKNOWLEDGE_RECORD_ENDPOINT)
                        .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expectedResponseBody));
    }

    @ParameterizedTest
    @MethodSource("getMixedCaseIds")
    void When_SendAcknowledgeRequestWithLowerAndUppercaseConversationIdHeader_Expect_ResponseStatusCode200(String conversationId)
        throws Exception {
        // when
        addMigrationRequestAndLogWithStatus(conversationId, MIGRATION_COMPLETED);
        mockMvc.perform(post(ACKNOWLEDGE_RECORD_ENDPOINT)
                .header(CONVERSATION_ID_HEADER, conversationId)
                .header(CONFIRMATION_RESPONSE_HEADER, ConfirmationResponse.ACCEPTED))
            // then
            .andExpect(status().isOk());
    }

    @Test
    public void sendAcknowledgeRequestWithMissingConfirmationResponseHeader() throws Exception {
        var conversationId = UUID.randomUUID().toString();
        var expectedResponseBody = readResourceAsString("/responses/acknowledge-record/badRequestResponseBody.json")
                .replace("{{header}}", CONFIRMATION_RESPONSE_HEADER);

        mockMvc.perform(post(ACKNOWLEDGE_RECORD_ENDPOINT)
                        .header(CONVERSATION_ID_HEADER, conversationId))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expectedResponseBody));
    }

    private void addMigrationRequestAndLogWithStatus(String conversationId, MigrationStatus status) {
        // Currently, when requests are created - they are uppercase.
        conversationId = conversationId.toUpperCase();

        patientMigrationRequestDao.addNewRequest(PATIENT_NUMBER, conversationId, LOSING_PRACTICE_ODS, WINNING_PRACTICE_ODS);
        patientMigrationRequestDao.saveBundleAndInboundMessageData(conversationId, BUNDLE_VALUE, INBOUND_MESSAGE_VALUE);
        migrationStatusLogService.addMigrationStatusLog(status, conversationId, null, null);
    }

    private static Stream<Arguments> getMixedCaseIds() {
        return Stream.of(
            Arguments.of(UUID.randomUUID().toString()),
            Arguments.of(UUID.randomUUID().toString().toUpperCase())
        );
    }
}