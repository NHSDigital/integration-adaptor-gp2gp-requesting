package uk.nhs.adaptors.pss.translator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.FailedProcessHandlingService;
import uk.nhs.adaptors.pss.translator.service.XPathService;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
import static uk.nhs.adaptors.common.enums.MigrationStatus.FINAL_ACK_SENT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class AcknowledgmentMessageHandler {
    private static final String ACK_TYPE_CODE_XPATH = "//MCCI_IN010000UK13/acknowledgement/@typeCode";
    private static final String NACK_REASON_CODE_PATH =
        "//MCCI_IN010000UK13/ControlActEvent/reason/justifyingDetectedIssueEvent/code/@code";
    private static final String ACK_TYPE_CODE = "AA";
    private static final String NACK_ERROR_TYPE_CODE = "AE";
    private static final String NACK_REJECT_TYPE_CODE = "AR";

    private final XPathService xPathService;
    private final MigrationStatusLogService migrationStatusLogService;

    private final FailedProcessHandlingService failedProcessHandlingService;

    public void handleMessage(InboundMessage inboundMessage, String conversationId) throws SAXException {

        Document document = xPathService.parseDocumentFromXml(inboundMessage.getPayload());
        String ackTypeCode = xPathService.getNodeValue(document, ACK_TYPE_CODE_XPATH);
        String nackReasonCode = null;

        if (failedProcessHandlingService.hasProcessFailed(conversationId)) {
            LOGGER.info("Received acknowledgement with type code [{}], but the migration has already failed", ackTypeCode);
            return;
        }

        var negativeAckError = isNegativeAckError(ackTypeCode);
        if (negativeAckError) {
            nackReasonCode = xPathService.getNodeValue(document, NACK_REASON_CODE_PATH);
            if (nackReasonCode == null) {
                nackReasonCode = "";
            }
        }

        MigrationStatus newMigrationStatus = getMigrationStatus(ackTypeCode, nackReasonCode);
        MigrationStatus currentMigrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(conversationId).getMigrationStatus();

        if (newMigrationStatus == null) {
            LOGGER.info("Unknown acknowledgement typeCode [{}]", ackTypeCode);
            return;
        }

        if (FINAL_ACK_SENT.equals(currentMigrationStatus) || MIGRATION_COMPLETED.equals(currentMigrationStatus)) {
            var loggerMessage = "Received an ack with type code {}, but the migration is complete";

            if (FINAL_ACK_SENT.equals(currentMigrationStatus)) {
                loggerMessage = loggerMessage + " and the EHR has been accepted";
            }

            LOGGER.info(loggerMessage, ackTypeCode);
            return;
        }

        if (negativeAckError) {
            migrationStatusLogService.addMigrationStatusLog(newMigrationStatus, conversationId, null, nackReasonCode);
        } else {
            migrationStatusLogService.addMigrationStatusLog(newMigrationStatus, conversationId, null, null);
        }
    }

    private static boolean isNegativeAckError(String ackTypeCode) {
        return NACK_ERROR_TYPE_CODE.equals(ackTypeCode) || NACK_REJECT_TYPE_CODE.equals(ackTypeCode);
    }

    private MigrationStatus getMigrationStatus(String ackTypeCode, String reasonCode) {
        return switch (ackTypeCode) {
            case ACK_TYPE_CODE -> EHR_EXTRACT_REQUEST_ACKNOWLEDGED;
            case NACK_ERROR_TYPE_CODE, NACK_REJECT_TYPE_CODE  -> switch (reasonCode) {
                case "06" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_PATIENT_NOT_REGISTERED;
                case "07" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_SENDER_NOT_CONFIGURED;
                case "10" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_EHR_GENERATION_ERROR;
                case "18" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MISFORMED_REQUEST;
                case "19" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_NOT_PRIMARY_HEALTHCARE_PROVIDER;
                case "24" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_GP2GP_MULTI_OR_NO_RESPONSES;
                case "99" -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
                default -> EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
            };
            default -> null;
        };
    }
}
