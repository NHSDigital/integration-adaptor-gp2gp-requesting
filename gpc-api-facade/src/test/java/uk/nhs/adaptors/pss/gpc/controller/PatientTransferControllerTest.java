package uk.nhs.adaptors.pss.gpc.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_REQUEST_TIMEOUT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.REQUEST_RECEIVED;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.CONVERSATION_ID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ODS;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ODS;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hl7.fhir.dstu3.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.pss.gpc.service.PatientTransferService;

@ExtendWith(MockitoExtension.class)
public class PatientTransferControllerTest {

    private static final String RESPONSE_BODY = "{responseBody}";
    private static final Parameters PARAMETERS = new Parameters();
    private static final String TO_ASID_VALUE = "123";
    private static final String FROM_ASID_VALUE = "321";
    private static final String TO_ODS_VALUE = "ABC";
    private static final String FROM_ODS_VALUE = "DEF";
    private static final Map<String, String> HEADERS = Map.of(
        TO_ASID, TO_ASID_VALUE,
        FROM_ASID, FROM_ASID_VALUE,
        TO_ODS, TO_ODS_VALUE,
        FROM_ODS, FROM_ODS_VALUE
    );

    @Mock
    private PatientTransferService patientTransferService;

    @InjectMocks
    private PatientTransferController controller;

    @Mock
    private FhirParser fhirParser;

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsNew() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS)).thenReturn(null);

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertThat(response.getBody()).isNull();
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsReceived() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(REQUEST_RECEIVED));

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertThat(response.getBody()).isNull();
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsInProgress() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_ACCEPTED));

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertThat(response.getBody()).isNull();
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsCompleted() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(MIGRATION_COMPLETED));
        when(patientTransferService.getBundleResource()).thenReturn(RESPONSE_BODY);

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(RESPONSE_BODY, response.getBody());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsUnsupported() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_ERROR));

        Exception exception = assertThrows(IllegalStateException.class, () -> controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE));

        assertEquals("Unsupported transfer status: EHR_EXTRACT_REQUEST_ERROR", exception.getMessage());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsEhrExtractNegativeAckIn500Group() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR));

        // The OperationOutcome does not effect the http status
        when(fhirParser.encodeToJson(any())).thenReturn("");

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsEhrExtractNegativeAckIn501Group() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED));

        // The OperationOutcome does not effect the http status
        when(fhirParser.encodeToJson(any())).thenReturn("");

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsEhrExtractNegativeAckin400Group() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST));

        // The OperationOutcome does not effect the http status
        when(fhirParser.encodeToJson(any())).thenReturn("");

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsEhrExtractNegativeAckIn404Group() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED));

        // The OperationOutcome does not effect the http status
        when(fhirParser.encodeToJson(any())).thenReturn("");

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void migratePatientStructuredRecordWhenTransferStatusIsErrorRequestTimeout() {
        when(patientTransferService.handlePatientMigrationRequest(PARAMETERS, HEADERS))
            .thenReturn(createMigrationStatusLog(ERROR_REQUEST_TIMEOUT));

        // The OperationOutcome does not effect the http status
        when(fhirParser.encodeToJson(any())).thenReturn("");

        ResponseEntity<String> response = controller.migratePatientStructuredRecord(
            PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    public void migratePatientStructureRecordWhenExistingPatientMigrationRequestInProgress() {

        when(patientTransferService.checkExistingPatientMigrationRequestInProgress(PARAMETERS))
            .thenReturn(CONVERSATION_ID);

        var response = controller.migratePatientStructuredRecord(PARAMETERS, TO_ASID_VALUE, FROM_ASID_VALUE, TO_ODS_VALUE, FROM_ODS_VALUE);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

    }

    private MigrationStatusLog createMigrationStatusLog(MigrationStatus status) {
        return MigrationStatusLog.builder()
            .id(1)
            .migrationStatus(status)
            .date(OffsetDateTime.now())
            .migrationRequestId(1)
            .build();
    }
}
