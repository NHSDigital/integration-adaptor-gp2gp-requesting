package uk.nhs.adaptors.pss.translator.service;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_TRANSLATED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.MIGRATION_COMPLETED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.EHR_EXTRACT_CANNOT_BE_PROCESSED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.ValidationException;
import javax.xml.transform.TransformerException;

import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.pss.translator.exception.AttachmentNotFoundException;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.exception.InlineAttachmentProcessingException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class InboundMessageMergingService {

    private final PatientAttachmentLogService patientAttachmentLogService;
    private final AttachmentHandlerService attachmentHandlerService;
    private final AttachmentReferenceUpdaterService attachmentReferenceUpdaterService;
    private final MigrationStatusLogService migrationStatusLogService;
    private final ObjectMapper objectMapper;
    private final FhirParser fhirParser;
    private final BundleMapperService bundleMapperService;
    private final PatientMigrationRequestDao migrationRequestDao;
    private final NackAckPrepInterface nackAckPreparationService;
    private final SkeletonProcessingService skeletonProcessingService;

    private static final String CONVERSATION_ID_HAS_NOT_BEEN_GIVEN = "Conversation Id has not been given";

    public boolean canMergeCompleteBundle(String conversationId) throws ValidationException {

        if (!StringUtils.hasText(conversationId)) {
            throw new ValidationException(CONVERSATION_ID_HAS_NOT_BEEN_GIVEN);
        }

        var undeletedLogs = getUndeletedLogsForConversation(conversationId);

        if (undeletedLogs.isEmpty()) {
            return false;
        }

        return undeletedLogs.stream().allMatch(log -> log.getUploaded().equals(true));
    }

    public void mergeAndBundleMessage(String conversationId) throws JAXBException, JsonProcessingException {

        if (!StringUtils.hasText(conversationId)) {
            throw new ValidationException(CONVERSATION_ID_HAS_NOT_BEEN_GIVEN);
        }

        PatientMigrationRequest migrationRequest = migrationRequestDao.getMigrationRequest(conversationId);
        var inboundMessage = objectMapper.readValue(migrationRequest.getInboundMessage(), InboundMessage.class);
        RCMRIN030000UKMessage payload = unmarshallString(inboundMessage.getPayload(), RCMRIN030000UKMessage.class);

        try {
            var attachmentLogs = getUndeletedLogsForConversation(conversationId);

            Optional<PatientAttachmentLog> skeletonLog = attachmentLogs.stream()
                .filter(PatientAttachmentLog::getSkeleton).findFirst();

            if (skeletonLog.isPresent()) {
                inboundMessage = skeletonProcessingService
                    .updateInboundMessageWithSkeleton(skeletonLog.get(), inboundMessage, conversationId);
            }

            // process attachments
            var bypassPayloadLoadingArray = new String[attachmentLogs.size()];
            Arrays.fill(bypassPayloadLoadingArray, "");

            var messageAttachments = attachmentHandlerService.buildInboundAttachmentsFromAttachmentLogs(
                    attachmentLogs,
                    Arrays.asList(bypassPayloadLoadingArray),
                    conversationId
            );
            var newPayloadStr = attachmentReferenceUpdaterService.replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                    messageAttachments,
                    conversationId,
                    inboundMessage.getPayload()
            );

            // process bundle
            inboundMessage.setPayload(newPayloadStr);
            payload = unmarshallString(inboundMessage.getPayload(), RCMRIN030000UKMessage.class);

            var attachments = patientAttachmentLogService.findAttachmentLogs(conversationId);

            var bundle = bundleMapperService.mapToBundle(payload, migrationRequest.getLosingPracticeOdsCode(), attachments);
            migrationStatusLogService.updatePatientMigrationRequestAndAddMigrationStatusLog(
                    conversationId,
                    fhirParser.encodeToJson(bundle),
                    objectMapper.writeValueAsString(inboundMessage),
                    EHR_EXTRACT_TRANSLATED,
                    null
            );
            migrationStatusLogService.addMigrationStatusLog(MIGRATION_COMPLETED, conversationId, null, null);

        } catch (InlineAttachmentProcessingException | SAXException | TransformerException
                 | JAXBException | AttachmentNotFoundException | JsonProcessingException e) {

            LOGGER.error("failed to merge Large Message Parts", e);
            nackAckPreparationService.sendNackMessage(LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED, payload, conversationId);

        } catch (BundleMappingException e) {
            LOGGER.error("failed to map to bundle", e);
            nackAckPreparationService.sendNackMessage(EHR_EXTRACT_CANNOT_BE_PROCESSED, payload, conversationId);
        }
    }

    private List<PatientAttachmentLog> getUndeletedLogsForConversation(String conversationId) throws ValidationException {

        if (!StringUtils.hasText(conversationId)) {
            throw new ValidationException(CONVERSATION_ID_HAS_NOT_BEEN_GIVEN);
        }

        var conversationAttachmentLogs = patientAttachmentLogService.findAttachmentLogs(conversationId);
        return conversationAttachmentLogs.stream()
            .filter(log -> log.getDeleted().equals(false))
            .toList();
    }
}
