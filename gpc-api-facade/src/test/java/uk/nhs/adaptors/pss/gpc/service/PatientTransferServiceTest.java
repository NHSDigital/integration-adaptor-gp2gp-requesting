package uk.nhs.adaptors.pss.gpc.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.common.enums.QueueMessageType.TRANSFER_REQUEST;
import static uk.nhs.adaptors.common.model.MigrationStatusGroups.IN_PROGRESS_STATUSES;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ODS;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ODS;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.common.model.TransferRequestMessage;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.testutil.CreateParametersUtil;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.dao.MigrationStatusLogDao;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.pss.gpc.amqp.PssQueuePublisher;

@ExtendWith(MockitoExtension.class)
public class PatientTransferServiceTest {

    private static final String PATIENT_NHS_NUMBER = "123456789";
    private static final String CONVERSATION_ID = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    private static final String LOSING_ODS_CODE = "D443";
    private static final String WINNING_ODS_CODE = "ABC";

    private static final Map<String, String> HEADERS = Map.of(
        TO_ASID, "1234",
        FROM_ASID, "5678",
        TO_ODS, LOSING_ODS_CODE,
        FROM_ODS, WINNING_ODS_CODE
    );

    @Mock
    private PatientMigrationRequestDao patientMigrationRequestDao;

    @Mock
    private MigrationStatusLogDao migrationStatusLogDao;

    @Mock
    private PssQueuePublisher pssQueuePublisher;

    @Mock
    private DateUtils dateUtils;

    @Mock
    private MDCService mdcService;

    @InjectMocks
    private PatientTransferService service;

    private Parameters parameters;

    @BeforeEach
    void setUp() {
        parameters = CreateParametersUtil.createValidParametersResource(PATIENT_NHS_NUMBER);
    }

    @Test
    public void handlePatientMigrationRequestWhenRequestIsNew() {

        var expectedPssQueueMessage = TransferRequestMessage.builder()
            .conversationId(CONVERSATION_ID)
            .patientNhsNumber(PATIENT_NHS_NUMBER)
            .toAsid(HEADERS.get(TO_ASID))
            .fromAsid(HEADERS.get(FROM_ASID))
            .toOds(HEADERS.get(TO_ODS))
            .fromOds(HEADERS.get(FROM_ODS))
            .messageType(TRANSFER_REQUEST)
            .build();

        var migrationRequestId = 1;
        OffsetDateTime now = OffsetDateTime.now();
        when(dateUtils.getCurrentOffsetDateTime()).thenReturn(now);
        when(patientMigrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(null);
        when(patientMigrationRequestDao.getMigrationRequestId(CONVERSATION_ID)).thenReturn(migrationRequestId);
        when(mdcService.getConversationId()).thenReturn(CONVERSATION_ID);

        MigrationStatusLog patientMigrationRequest = service.handlePatientMigrationRequest(parameters, HEADERS);

        assertThat(patientMigrationRequest).isNull();
        verify(pssQueuePublisher).sendToPssQueue(expectedPssQueueMessage);
        verify(patientMigrationRequestDao).addNewRequest(PATIENT_NHS_NUMBER, CONVERSATION_ID, LOSING_ODS_CODE, WINNING_ODS_CODE);
        verify(migrationStatusLogDao).addMigrationStatusLog(MigrationStatus.REQUEST_RECEIVED, now, migrationRequestId, null, null);
    }

    @Test
    public void handlePatientMigrationRequestWhenRequestIsNewAndConversationIdIsLowercase() {
        var expectedPssQueueMessage = TransferRequestMessage.builder()
            .conversationId(CONVERSATION_ID)
            .patientNhsNumber(PATIENT_NHS_NUMBER)
            .toAsid(HEADERS.get(TO_ASID))
            .fromAsid(HEADERS.get(FROM_ASID))
            .toOds(HEADERS.get(TO_ODS))
            .fromOds(HEADERS.get(FROM_ODS))
            .messageType(TRANSFER_REQUEST)
            .build();

        var migrationRequestId = 1;
        OffsetDateTime now = OffsetDateTime.now();
        when(dateUtils.getCurrentOffsetDateTime()).thenReturn(now);
        when(patientMigrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(null);
        when(patientMigrationRequestDao.getMigrationRequestId(CONVERSATION_ID)).thenReturn(migrationRequestId);
        when(mdcService.getConversationId()).thenReturn(CONVERSATION_ID.toLowerCase(Locale.ROOT));

        MigrationStatusLog patientMigrationRequest = service.handlePatientMigrationRequest(parameters, HEADERS);

        assertThat(patientMigrationRequest).isNull();
        verify(pssQueuePublisher).sendToPssQueue(expectedPssQueueMessage);
        verify(patientMigrationRequestDao).addNewRequest(PATIENT_NHS_NUMBER, CONVERSATION_ID, LOSING_ODS_CODE, WINNING_ODS_CODE);
        verify(migrationStatusLogDao).addMigrationStatusLog(MigrationStatus.REQUEST_RECEIVED, now, migrationRequestId, null, null);
    }

    @Test
    public void handlePatientMigrationRequestWhenRequestIsInProgress() {
        PatientMigrationRequest expectedPatientMigrationRequest = createPatientMigrationRequest();
        MigrationStatusLog expectedMigrationStatusLog = createMigrationStatusLog();

        when(mdcService.getConversationId()).thenReturn(CONVERSATION_ID);
        when(patientMigrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(expectedPatientMigrationRequest);
        when(migrationStatusLogDao.getLatestMigrationStatusLog(expectedPatientMigrationRequest.getId()))
            .thenReturn(expectedMigrationStatusLog);

        MigrationStatusLog patientMigrationRequest = service.handlePatientMigrationRequest(parameters, HEADERS);

        assertEquals(expectedMigrationStatusLog, patientMigrationRequest);
        verifyNoInteractions(pssQueuePublisher);
        verify(patientMigrationRequestDao).getMigrationRequest(CONVERSATION_ID);
        verifyNoMoreInteractions(patientMigrationRequestDao);
    }

    @Test
    public void handlePatientMigrationRequestWhenRequestIsInProgressAndCalledWithALowercaseConversationId() {
        PatientMigrationRequest expectedPatientMigrationRequest = createPatientMigrationRequest();
        MigrationStatusLog expectedMigrationStatusLog = createMigrationStatusLog();

        var lowercaseConversationId = CONVERSATION_ID.toLowerCase(Locale.ROOT);
        when(mdcService.getConversationId()).thenReturn(lowercaseConversationId);
        when(patientMigrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(expectedPatientMigrationRequest);
        when(migrationStatusLogDao.getLatestMigrationStatusLog(expectedPatientMigrationRequest.getId()))
            .thenReturn(expectedMigrationStatusLog);

        MigrationStatusLog patientMigrationRequest = service.handlePatientMigrationRequest(parameters, HEADERS);

        assertEquals(expectedMigrationStatusLog, patientMigrationRequest);
        verifyNoInteractions(pssQueuePublisher);
        verify(patientMigrationRequestDao).getMigrationRequest(CONVERSATION_ID);
        verifyNoMoreInteractions(patientMigrationRequestDao);
    }

    @Test
    public void checkExistingPatientMigrationRequestInProgressWhenNoExistingRequest() {

        when(mdcService.getConversationId())
            .thenReturn(CONVERSATION_ID);
        when(patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(PATIENT_NHS_NUMBER))
                        .thenReturn(null);

        var existingConversationId = service.checkExistingPatientMigrationRequestInProgress(parameters);

        assertThat(existingConversationId).isNull();
    }

    @Test
    public void checkExistingPatientMigrationRequestInProgressWhenMigrationStatusLogNotYetCreated() {
        // this test deals with the edge case that two requests with different conversationId's are fired in quick
        // succession and the migrationStatusLog from the first request received has not yet been written

        when(mdcService.getConversationId())
                .thenReturn(CONVERSATION_ID);
        when(patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(PATIENT_NHS_NUMBER))
                .thenReturn(null);

        var existingConversationId = service.checkExistingPatientMigrationRequestInProgress(parameters);

        assertThat(existingConversationId).isNull();
    }

    @ParameterizedTest
    @MethodSource("generateInProgressStatuses")
    public void checkExistingPatientMigrationRequestInProgressWhenExistingMigrationInProcess(MigrationStatus status) {
        final String newConversationId = "00000000-0000-4001-0000-000000000000";
        final PatientMigrationRequest patientMigrationRequest = createPatientMigrationRequest();
        final MigrationStatusLog migrationStatusLog = createMigrationStatusLog(status);

        when(mdcService.getConversationId())
                .thenReturn(newConversationId);
        when(patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(PATIENT_NHS_NUMBER))
                .thenReturn(patientMigrationRequest);
        when(migrationStatusLogDao.getLatestMigrationStatusLog(1))
                .thenReturn(migrationStatusLog);

        var existingConversationId = service.checkExistingPatientMigrationRequestInProgress(parameters);

        assertThat(existingConversationId).isNotNull();
        assertEquals(CONVERSATION_ID, existingConversationId);
    }

    @ParameterizedTest
    @MethodSource("generateCompletedStatuses")
    public void checkExistingPatientMigrationRequestInProgressWhenPreviousMigrationCompleted(MigrationStatus status) {

        final String newConversationId = UUID.randomUUID().toString().toUpperCase();
        final PatientMigrationRequest patientMigrationRequest = createPatientMigrationRequest();
        final MigrationStatusLog migrationStatusLog = createMigrationStatusLog(status);

        when(mdcService.getConversationId())
            .thenReturn(newConversationId);
        when(patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(PATIENT_NHS_NUMBER))
                .thenReturn(patientMigrationRequest);
        when(migrationStatusLogDao.getLatestMigrationStatusLog(1))
            .thenReturn(migrationStatusLog);

        var existingConversationId = service.checkExistingPatientMigrationRequestInProgress(parameters);

        assertThat(existingConversationId).isNull();
    }

    @Test
    public void checkExistingPatientMigrationRequestInProgressWhenConversationIdsMatch() {
        final PatientMigrationRequest patientMigrationRequest = createPatientMigrationRequest();
        final MigrationStatusLog migrationStatusLog = createMigrationStatusLog();

        when(mdcService.getConversationId())
            .thenReturn(CONVERSATION_ID);
        when(patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(PATIENT_NHS_NUMBER))
                .thenReturn(patientMigrationRequest);
        when(migrationStatusLogDao.getLatestMigrationStatusLog(1))
                .thenReturn(migrationStatusLog);

        var existingConversationId = service.checkExistingPatientMigrationRequestInProgress(parameters);

        assertThat(existingConversationId).isNull();
    }

    private static Stream<MigrationStatus> generateInProgressStatuses() {
        return IN_PROGRESS_STATUSES.stream();
    }

    private static Stream<MigrationStatus> generateCompletedStatuses() {
        return Stream.of(MigrationStatus.values())
                .filter(status -> !IN_PROGRESS_STATUSES.contains(status));
    }

    private PatientMigrationRequest createPatientMigrationRequest() {
        return PatientMigrationRequest.builder()
            .id(1)
            .conversationId(CONVERSATION_ID)
            .patientNhsNumber(PATIENT_NHS_NUMBER)
            .build();
    }

    private MigrationStatusLog createMigrationStatusLog() {
        return MigrationStatusLog.builder()
            .id(1)
            .migrationStatus(MigrationStatus.REQUEST_RECEIVED)
            .date(OffsetDateTime.now())
            .migrationRequestId(1)
            .gp2gpErrorCode("99")
            .build();
    }

    private MigrationStatusLog createMigrationStatusLog(MigrationStatus status) {
        return MigrationStatusLog.builder()
                .id(1)
                .migrationStatus(status)
                .date(OffsetDateTime.now())
                .migrationRequestId(1)
                .gp2gpErrorCode("99")
                .build();
    }

}
