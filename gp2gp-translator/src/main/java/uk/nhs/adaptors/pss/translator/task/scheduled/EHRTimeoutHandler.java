package uk.nhs.adaptors.pss.translator.task.scheduled;

import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_TRANSLATED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_REQUEST_TIMEOUT;
import static uk.nhs.adaptors.common.enums.MigrationStatus.REQUEST_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_TIMEOUT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.xml.bind.JAXBException;

import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.connector.service.PatientMigrationRequestService;
import uk.nhs.adaptors.pss.translator.config.TimeoutProperties;
import uk.nhs.adaptors.pss.translator.exception.SdsRetrievalException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.service.PersistDurationService;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;
import uk.nhs.adaptors.pss.translator.util.InboundMessageUtil;
import uk.nhs.adaptors.pss.translator.util.OutboundMessageUtil;

@Slf4j
@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class EHRTimeoutHandler {

    private static final String EHR_EXTRACT_MESSAGE_NAME = "RCMR_IN030000UK06";
    private static final String COPC_MESSAGE_NAME = "COPC_IN000001UK01";
    private static final Integer MAX_TIMEOUT_OVERWRITE = 172800;
    private final PersistDurationService persistDurationService;
    private final PatientMigrationRequestService migrationRequestService;
    private final MDCService mdcService;
    private final TimeoutProperties timeoutProperties;
    private final SendNACKMessageHandler sendNACKMessageHandler;
    private final OutboundMessageUtil outboundMessageUtil;
    private final InboundMessageUtil inboundMessageUtil;
    private final MigrationStatusLogService migrationStatusLogService;
    private final PatientAttachmentLogService patientAttachmentLogService;
    private final DateUtils dateUtils;

    private static final List<MigrationStatus> PRE_EHR_PARSED_STATUS_LIST = List.of(
        REQUEST_RECEIVED,
        EHR_EXTRACT_REQUEST_ACCEPTED,
        EHR_EXTRACT_REQUEST_ACKNOWLEDGED,
        EHR_EXTRACT_RECEIVED
    );

    private static final List<MigrationStatus> REQUESTS_WITH_ATTACHMENTS_STATUS_LIST = List.of(
        EHR_EXTRACT_PROCESSING,
        CONTINUE_REQUEST_ACCEPTED,
        COPC_MESSAGE_RECEIVED,
        COPC_MESSAGE_PROCESSING,
        COPC_ACKNOWLEDGED
    );

    @Scheduled(cron = "${timeout.cronTime}")
    public void checkForTimeouts() {
        LOGGER.info("running scheduled task to check for timeouts");

        List<PatientMigrationRequest> preEhrParsedRequests = migrationRequestService
            .getMigrationRequestsByMigrationStatusIn(PRE_EHR_PARSED_STATUS_LIST);
        preEhrParsedRequests.forEach(this::handleRequestTimeout);

        // Ehr Extract Translated is not the final state for an EHR, so we cannot guarantee it has attachments
        List<PatientMigrationRequest> translatedRequests = migrationRequestService
            .getMigrationRequestsByMigrationStatusIn(List.of(EHR_EXTRACT_TRANSLATED));
        translatedRequests.forEach(migrationRequest -> handleMigrationTimeout(migrationRequest, UNEXPECTED_CONDITION));

        List<PatientMigrationRequest> requestsWithAttachments = migrationRequestService
            .getMigrationRequestsByMigrationStatusIn(REQUESTS_WITH_ATTACHMENTS_STATUS_LIST);
        requestsWithAttachments.forEach(migrationRequest -> handleMigrationTimeout(migrationRequest,
            LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED));
    }

    private void handleRequestTimeout(PatientMigrationRequest migrationRequest) {
        String conversationId = migrationRequest.getConversationId();
        mdcService.applyConversationId(conversationId);

        try {
            long timeout;
            Duration ehrPersistDuration = persistDurationService.getPersistDurationFor(migrationRequest, EHR_EXTRACT_MESSAGE_NAME);
            OffsetDateTime currentTime = dateUtils.getCurrentOffsetDateTime();

            timeout = timeoutProperties.getEhrExtractWeighting() * ehrPersistDuration.getSeconds();

            LOGGER.debug("Request timeout calculated as [{}] seconds", timeout);

            MigrationStatusLog migrationStatusLog = migrationStatusLogService.getLatestMigrationStatusLog(conversationId);
            var timeoutDateTime = migrationStatusLog.getDate().plusSeconds(timeout);
            LOGGER.debug("Timeout datetime calculated as [{}]", timeoutDateTime);

            if (timeoutDateTime.isBefore(currentTime)) {
                LOGGER.info("Migration Request timed out at [{}]", timeoutDateTime);
                migrationStatusLogService.addMigrationStatusLog(ERROR_REQUEST_TIMEOUT, conversationId, null, null);
            }

        } catch (SdsRetrievalException e) {
            LOGGER.error("Error retrieving persist duration: [{}]", e.getMessage());
        } finally {
            mdcService.applyConversationId("");
        }
    }

    private void handleMigrationTimeout(PatientMigrationRequest migrationRequest, NACKReason reason) {

        String conversationId = migrationRequest.getConversationId();
        mdcService.applyConversationId(conversationId);

        try {
            long timeout;
            Duration ehrPersistDuration = persistDurationService.getPersistDurationFor(migrationRequest, EHR_EXTRACT_MESSAGE_NAME);
            InboundMessage message = inboundMessageUtil.readMessage(migrationRequest.getInboundMessage());
            ZonedDateTime messageTimestamp = inboundMessageUtil.parseMessageTimestamp(message.getEbXML());
            ZonedDateTime currentTime = ZonedDateTime.now(messageTimestamp.getZone());
            long numberCOPCMessages = patientAttachmentLogService.countAttachmentsForMigrationRequest(migrationRequest.getId());

            if (numberCOPCMessages > 0) {
                Duration copcPersistDuration = persistDurationService.getPersistDurationFor(migrationRequest, COPC_MESSAGE_NAME);

                timeout = (timeoutProperties.getEhrExtractWeighting() * ehrPersistDuration.getSeconds())
                        + (timeoutProperties.getCopcWeighting() * numberCOPCMessages * copcPersistDuration.getSeconds());

                LOGGER.debug("Large message timeout calculated as [{}] seconds", timeout);
            } else {
                timeout = timeoutProperties.getEhrExtractWeighting() * ehrPersistDuration.getSeconds();

                LOGGER.debug("Non large message timeout calculated as [{}] seconds", timeout);
            }

            if (timeoutProperties.isMigrationTimeoutOverride() && MAX_TIMEOUT_OVERWRITE < timeout) {
                timeout = MAX_TIMEOUT_OVERWRITE;
                LOGGER.info("Migration Request timeout overwritten to 48 hours");
            }

            ZonedDateTime timeoutDateTime = messageTimestamp.plusSeconds(timeout);
            LOGGER.debug("Timeout datetime calculated as [{}]", timeoutDateTime);

            if (timeoutDateTime.isBefore(currentTime)) {
                LOGGER.info("Migration timed out at [{}]", timeoutDateTime);
                sendNackMessage(message, conversationId, reason);
            }
        } catch (SdsRetrievalException e) {
            LOGGER.error("Error retrieving persist duration: [{}]", e.getMessage());
        } catch (JsonProcessingException | SAXException | DateTimeParseException | JAXBException e) {
            LOGGER.error("Error parsing inbound message from database");
            migrationStatusLogService
                .addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR, conversationId, null, UNEXPECTED_CONDITION.getCode());
        } finally {
            mdcService.applyConversationId("");
        }
    }

    private void sendNackMessage(InboundMessage inboundMessage, String conversationId, NACKReason reason) throws JAXBException {

        RCMRIN030000UKMessage payload = unmarshallString(inboundMessage.getPayload(), RCMRIN030000UKMessage.class);

        NACKMessageData messageData = NACKMessageData
            .builder()
            .nackReason(reason)
            .fromAsid(outboundMessageUtil.parseFromAsid(payload))
            .toAsid(outboundMessageUtil.parseToAsid(payload))
            .toOdsCode(outboundMessageUtil.parseToOdsCode(payload))
            .messageRef(outboundMessageUtil.parseMessageRef(payload))
            .conversationId(conversationId)
            .build();

        LOGGER.debug("EHR Extract message timed out: sending NACK message");
        if (sendNACKMessageHandler.prepareAndSendMessage(messageData)) {

            if (reason == LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED) {
                migrationStatusLogService
                    .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(), conversationId, null, reason.getCode());
            } else {
                migrationStatusLogService.addMigrationStatusLog(reason.getMigrationStatus(), conversationId, null, reason.getCode());
            }
        }
    }
}
