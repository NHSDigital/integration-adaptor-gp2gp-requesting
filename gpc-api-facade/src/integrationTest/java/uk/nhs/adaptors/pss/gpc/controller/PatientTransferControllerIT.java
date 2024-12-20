package uk.nhs.adaptors.pss.gpc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_REQUEST_TIMEOUT;

import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.dao.MigrationStatusLogDao;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext
@AutoConfigureMockMvc
@Transactional
public class PatientTransferControllerIT {

    private static final int NHS_NUMBER_MIN_MAX_LENGTH = 10;
    private static final String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";
    private static final String MIGRATE_PATIENT_RECORD_ENDPOINT = "/Patient/$gpc.migratestructuredrecord";
    private static final String VALID_REQUEST_BODY_PATH = "/requests/migrate-patient-record/validRequestBody.json";
    private static final String UNPROCESSABLE_ENTITY_RESPONSE_BODY_PATH =
            "/responses/migrate-patient-record/unprocessableEntityResponseBody.json";
    private static final String TRANSFER_IN_PROGRESS_RESPONSE_BODY_PATH =
            "/responses/migrate-patient-record/transferInProgressResponseBody.json";
    private static final HttpHeaders REQUIRED_HEADERS = generateHeaders();
    private static final String CONVERSATION_ID_HEADER = "ConversationId";
    private static final String LOSING_PRACTICE_ODS = "F765";
    private static final String WINNING_PRACTICE_ODS = "B943";
    private static final String MOCK_PATIENT_NUMBER = "123456789";
    private static final String EXAMPLE_JSON_BUNDLE = "/responses/json/exampleBundle.json";

    @Autowired
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Autowired
    private MigrationStatusLogDao migrationStatusLogDao;

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Autowired
    private FhirParser fhirParser;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void sendNewPatientTransferRequest() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var conversationId = generateConversationId();

        var migrationRequestBefore = patientMigrationRequestDao.getMigrationRequest(conversationId);
        assertThat(migrationRequestBefore).isNull();

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, conversationId)
                    .content(requestBody))
            .andExpect(status().isAccepted());

        var migrationRequestAfterFirstRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        verifyPatientMigrationRequest(migrationRequestAfterFirstRequest, MigrationStatus.REQUEST_RECEIVED);
    }

    @Test
    public void sendPatientTransferRequestWhenTransferIsAlreadyInProgress() throws Exception {
        var conversationId = generateConversationId();
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);

        var migrationRequestBefore = patientMigrationRequestDao.getMigrationRequest(conversationId);
        assertThat(migrationRequestBefore).isNull();

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, conversationId)
                    .content(requestBody))
            .andExpect(status().isAccepted());

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, conversationId)
                    .content(requestBody))
            .andExpect(status().isNoContent());

        var migrationRequestAfterSecondRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        verifyPatientMigrationRequest(migrationRequestAfterSecondRequest, MigrationStatus.REQUEST_RECEIVED);
    }

    @Test
    public void sendPatientTransferRequestWithDifferentConversationIdWhenTransferIsAlreadyInProgress() throws Exception {
        var conversationId = generateConversationId();
        var secondConversationId = generateConversationId();
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var expectedResponseBody = readResourceAsString(TRANSFER_IN_PROGRESS_RESPONSE_BODY_PATH)
                .replace("{{conversationId}}", conversationId);

        var migrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        assertThat(migrationRequest).isNull();

        mockMvc.perform(
                        post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                                .contentType(APPLICATION_FHIR_JSON_VALUE)
                                .headers(REQUIRED_HEADERS)
                                .header(CONVERSATION_ID_HEADER, conversationId)
                                .content(requestBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(
                        post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                                .contentType(APPLICATION_FHIR_JSON_VALUE)
                                .headers(REQUIRED_HEADERS)
                                .header(CONVERSATION_ID_HEADER, secondConversationId)
                                .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(expectedResponseBody))
                .andReturn();
    }

    @Test
    public void sendPatientTransferRequestWhenPreviousTransferForThatNhsNumberHasCompleted() throws Exception {
        var conversationId = generateConversationId();
        var secondConversationId = generateConversationId();
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);

        completePatientMigrationJourney(conversationId);

        mockMvc.perform(
                        post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                                .contentType(APPLICATION_FHIR_JSON_VALUE)
                                .headers(REQUIRED_HEADERS)
                                .header(CONVERSATION_ID_HEADER, secondConversationId)
                                .content(requestBody))
                .andExpect(status().isAccepted());
    }

    @Test
    public void sendPatientTransferRequestAndExpectTimeErrorResponseBody() throws Exception {
        var conversationId = generateConversationId();
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var expectedErrorResponseBody = readResourceAsString("/responses/migrate-patient-record/badMigrationStatusResponseBody.json");

        completePatientMigrationJourneyWithError(conversationId);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, conversationId)
                    .content(requestBody))
            .andExpect(status().is5xxServerError())
            .andExpect(content().json(expectedErrorResponseBody, true))
            .andReturn();
    }

    @Test
    public void sendPatientTransferRequestWithIncorrectContentTypeHeader() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var expectedResponseBody = readResourceAsString("/responses/common/unsupportedMediaTypeResponseBody.json");

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(MediaType.TEXT_PLAIN)
                    .headers(REQUIRED_HEADERS)
                    .content(requestBody))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestToNonexistentEndpoint() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var nonexistentEndpoint = "/Patient/$gpc.migrateCatRecord";
        var expectedResponseBody = readResourceAsString("/responses/common/notFoundResponseBody.json")
            .replace("{{endpointUrl}}", nonexistentEndpoint);

        mockMvc.perform(
                post(nonexistentEndpoint)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestUsingIncorrectMethod() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var expectedResponseBody = readResourceAsString("/responses/common/methodNotAllowedResponseBody.json")
            .replace("{{requestMethod}}", "PATCH");

        mockMvc.perform(
                patch(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .content(requestBody))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestWithoutNhsNumber() throws Exception {
        var requestBody = readResourceAsString("/requests/migrate-patient-record/missingNhsNumberRequestBody.json");
        var expectedResponseBody = readResourceAsString(UNPROCESSABLE_ENTITY_RESPONSE_BODY_PATH);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .content(requestBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestWithInvalidBody() throws Exception {
        var requestBody = getRequestBody("/requests/migrate-patient-record/invalidRequestBody.json");
        var expectedResponseBody = readResourceAsString(UNPROCESSABLE_ENTITY_RESPONSE_BODY_PATH);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .content(requestBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestWithEmptyBody() throws Exception {
        var expectedResponseBody = readResourceAsString(UNPROCESSABLE_ENTITY_RESPONSE_BODY_PATH);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .content(StringUtils.EMPTY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void sendPatientTransferRequestWithoutRequiredHeaders() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var expectedResponseBody = readResourceAsString("/responses/migrate-patient-record/badRequestResponseBody.json");

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(content().json(expectedResponseBody));
    }

    @Test
    public void handleCompletedMigrationPatientRequest() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var conversationId = generateConversationId();

        completePatientMigrationJourney(conversationId);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, conversationId)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().json(readResourceAsString(EXAMPLE_JSON_BUNDLE)));

        var migrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        verifyPatientMigrationRequest(migrationRequest, MIGRATION_COMPLETED);
    }

    // This is a use case test to make sure we can pull bundles back with case insensitive conversation ids.
    @Test
    public void handleCompletedMigrationPatientRequestWithLowercaseConversationId() throws Exception {
        var requestBody = getRequestBody(VALID_REQUEST_BODY_PATH);
        var conversationId = generateConversationId();
        var lowercaseConversationId = conversationId.toLowerCase(Locale.ROOT);

        completePatientMigrationJourney(conversationId);

        mockMvc.perform(
                post(MIGRATE_PATIENT_RECORD_ENDPOINT)
                    .contentType(APPLICATION_FHIR_JSON_VALUE)
                    .headers(REQUIRED_HEADERS)
                    .header(CONVERSATION_ID_HEADER, lowercaseConversationId)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().json(readResourceAsString(EXAMPLE_JSON_BUNDLE)));

        var migrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        verifyPatientMigrationRequest(migrationRequest, MIGRATION_COMPLETED);
    }

    private String getRequestBody(String path) {
        var patientNhsNumber = generatePatientNhsNumber();
        return readResourceAsString(path).replace("{{nhsNumber}}", patientNhsNumber);
    }

    private void verifyPatientMigrationRequest(PatientMigrationRequest patientMigrationRequest, MigrationStatus status) {
        var migrationStatusLog = migrationStatusLogDao.getLatestMigrationStatusLog(patientMigrationRequest.getId());
        assertThat(patientMigrationRequest).isNotNull();
        assertThat(patientMigrationRequest.getLosingPracticeOdsCode()).isEqualTo(LOSING_PRACTICE_ODS);
        assertThat(migrationStatusLog.getMigrationStatus()).isEqualTo(status);
    }

    private String generatePatientNhsNumber() {
        return RandomStringUtils.randomNumeric(NHS_NUMBER_MIN_MAX_LENGTH, NHS_NUMBER_MIN_MAX_LENGTH);
    }

    private String generateConversationId() {
        return UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private static HttpHeaders generateHeaders() {
        var headers = new HttpHeaders();
        headers.set("from-asid", "123456");
        headers.set("to-asid", "32145");
        headers.set("from-ods", WINNING_PRACTICE_ODS);
        headers.set("to-ods", LOSING_PRACTICE_ODS);

        return headers;
    }

    private void completePatientMigrationJourney(String conversationId) {
        patientMigrationRequestDao.addNewRequest(MOCK_PATIENT_NUMBER, conversationId, LOSING_PRACTICE_ODS, WINNING_PRACTICE_ODS);
        patientMigrationRequestDao.saveBundleAndInboundMessageData(conversationId, readResourceAsString(EXAMPLE_JSON_BUNDLE),
            StringUtils.EMPTY);
        migrationStatusLogService.addMigrationStatusLog(MIGRATION_COMPLETED, conversationId, null, null);
    }

    private void completePatientMigrationJourneyWithError(String conversationId) {
        patientMigrationRequestDao.addNewRequest(MOCK_PATIENT_NUMBER, conversationId, LOSING_PRACTICE_ODS, WINNING_PRACTICE_ODS);
        patientMigrationRequestDao.saveBundleAndInboundMessageData(conversationId, readResourceAsString(EXAMPLE_JSON_BUNDLE),
                                                                   StringUtils.EMPTY);
        migrationStatusLogService.addMigrationStatusLog(ERROR_REQUEST_TIMEOUT, conversationId, null, "25");
    }
}
