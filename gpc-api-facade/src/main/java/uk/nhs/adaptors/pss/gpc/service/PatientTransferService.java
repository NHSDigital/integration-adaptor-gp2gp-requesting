package uk.nhs.adaptors.pss.gpc.service;

import static uk.nhs.adaptors.common.enums.MigrationStatus.REQUEST_RECEIVED;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.FROM_ODS;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ASID;
import static uk.nhs.adaptors.pss.gpc.controller.header.HttpHeaders.TO_ODS;
import static uk.nhs.adaptors.pss.gpc.util.fhir.ParametersUtils.getNhsNumberFromParameters;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.hl7.fhir.dstu3.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.common.enums.QueueMessageType;
import uk.nhs.adaptors.common.model.MigrationStatusGroups;
import uk.nhs.adaptors.common.model.TransferRequestMessage;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.dao.MigrationStatusLogDao;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.pss.gpc.amqp.PssQueuePublisher;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PatientTransferService {
    private final PatientMigrationRequestDao patientMigrationRequestDao;
    private final MigrationStatusLogDao migrationStatusLogDao;
    private final PssQueuePublisher pssQueuePublisher;
    private final DateUtils dateUtils;
    private final MDCService mdcService;

    public MigrationStatusLog handlePatientMigrationRequest(Parameters parameters, Map<String, String> headers) {
        var conversationId = mdcService.getConversationId();
        conversationId = conversationId.toUpperCase(Locale.ROOT);

        PatientMigrationRequest patientMigrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);

        if (patientMigrationRequest == null) {
            var patientNhsNumber = getNhsNumberFromParameters(parameters).get().getValue();
            patientMigrationRequestDao.addNewRequest(patientNhsNumber, conversationId, headers.get(TO_ODS), headers.get(FROM_ODS));

            int addedId = patientMigrationRequestDao.getMigrationRequestId(UUID.fromString(conversationId));
            migrationStatusLogDao.addMigrationStatusLog(REQUEST_RECEIVED, dateUtils.getCurrentOffsetDateTime(), addedId, null, null);

            var pssMessage = createTransferRequestMessage(patientNhsNumber, headers, conversationId);
            pssQueuePublisher.sendToPssQueue(pssMessage);
        } else {
            return migrationStatusLogDao.getLatestMigrationStatusLog(patientMigrationRequest.getId());
        }
        return null;
    }

    public String checkExistingPatientMigrationRequestInProgress(Parameters parameters) {
        var patientNhsNumber = getNhsNumberFromParameters(parameters).get().getValue();

        var existingConversationId = getConversationIdOfIncompleteMigrationRequest(patientNhsNumber);

        var internalConversationId = mdcService.getConversationId().toUpperCase(Locale.ROOT);
        return internalConversationId.equals(existingConversationId) ? null : existingConversationId;
    }

    private String getConversationIdOfIncompleteMigrationRequest(String patientNhsNumber) {
        var migrationRequest = patientMigrationRequestDao.getLatestMigrationRequestByPatientNhsNumber(patientNhsNumber);
        if (migrationRequest == null) {
            return null;
        }

        var migrationStatusLog = migrationStatusLogDao.getLatestMigrationStatusLog(migrationRequest.getId());

        // edge case of second request occurring before the first migration status log has been written
        if (migrationStatusLog == null) {
            return migrationRequest.getConversationId();
        }

        if (MigrationStatusGroups.IN_PROGRESS_STATUSES.contains(migrationStatusLog.getMigrationStatus())) {
            return migrationRequest.getConversationId();
        }

        return null;
    }

    public String getBundleResource() {
        var conversationId = mdcService.getConversationId().toUpperCase(Locale.ROOT);
        PatientMigrationRequest patientMigrationRequest = patientMigrationRequestDao.getMigrationRequest(conversationId);
        return patientMigrationRequest.getBundleResource();
    }

    private TransferRequestMessage createTransferRequestMessage(String patientNhsNumber, Map<String, String> headers,
        String conversationId) {
        return TransferRequestMessage.builder()
            .conversationId(conversationId)
            .patientNhsNumber(patientNhsNumber)
            .messageType(QueueMessageType.TRANSFER_REQUEST)
            .toAsid(headers.get(TO_ASID))
            .fromAsid(headers.get(FROM_ASID))
            .toOds(headers.get(TO_ODS))
            .fromOds(headers.get(FROM_ODS))
            .build();
    }
}
