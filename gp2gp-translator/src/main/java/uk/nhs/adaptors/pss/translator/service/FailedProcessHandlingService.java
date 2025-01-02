package uk.nhs.adaptors.pss.translator.service;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_FAILED_TO_INTEGRATE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_NON_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_NEGATIVE_ACK_SUPPRESSED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_EXTRACT_CANNOT_BE_PROCESSED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_GENERAL_FAILURE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_REASSEMBLY_FAILURE;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_TIMEOUT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_REQUEST_TIMEOUT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_GENERAL_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_TIMEOUT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.util.List;

import org.hl7.v3.COPCIN000001UK01Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class FailedProcessHandlingService {

    private final MigrationStatusLogService migrationStatusLogService;
    private final NackAckPrepInterface nackAckPreparationService;

    private final SendNACKMessageHandler sendNACKMessageHandler;

    private static final List<MigrationStatus> FAILED_MIGRATION_STATUSES = List.of(
        ERROR_LRG_MSG_REASSEMBLY_FAILURE,
        ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED,
        ERROR_LRG_MSG_GENERAL_FAILURE,
        ERROR_LRG_MSG_TIMEOUT,
        ERROR_REQUEST_TIMEOUT,
        EHR_GENERAL_PROCESSING_ERROR,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN,
        EHR_EXTRACT_NEGATIVE_ACK_ABA_INCORRECT_PATIENT,
        EHR_EXTRACT_NEGATIVE_ACK_NON_ABA_INCORRECT_PATIENT,
        EHR_EXTRACT_NEGATIVE_ACK_FAILED_TO_INTEGRATE,
        EHR_EXTRACT_NEGATIVE_ACK_SUPPRESSED,
        ERROR_EXTRACT_CANNOT_BE_PROCESSED
    );

    private static final List<MigrationStatus> INCUMBENT_NACK_STATUSES = List.of(
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES,
        EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN
    );

    public boolean hasProcessFailed(String conversationId) {
        MigrationStatus migrationStatus = getMigrationStatus(conversationId);

        return FAILED_MIGRATION_STATUSES.contains(migrationStatus);
    }

    public void handleFailedProcess(COPCIN000001UK01Message copcMessage, String conversationId) {

        var migrationStatus = getMigrationStatus(conversationId);

        NACKReason nackReason;

        if (INCUMBENT_NACK_STATUSES.contains(migrationStatus)) {
            nackReason = UNEXPECTED_CONDITION;
        } else {
            nackReason = switch (migrationStatus) {
                case ERROR_LRG_MSG_TIMEOUT, ERROR_REQUEST_TIMEOUT -> LARGE_MESSAGE_TIMEOUT;
                default -> LARGE_MESSAGE_GENERAL_FAILURE;
            };
        }

        LOGGER.info("Received COPC Message [Message ID: {}], but the transfer process has already failed. Responding with NACK code {}",
            copcMessage.getId().getRoot(), nackReason.getCode());

        var nackMessageData = nackAckPreparationService
            .prepareNackMessageData(nackReason, copcMessage, conversationId);

        sendNACKMessageHandler.prepareAndSendMessage(nackMessageData);
    }

    private MigrationStatus getMigrationStatus(String conversationId) {
        return migrationStatusLogService.getLatestMigrationStatusLog(conversationId)
            .getMigrationStatus();
    }
}


