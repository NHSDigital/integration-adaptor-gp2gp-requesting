package uk.nhs.adaptors.pss.translator.task;

import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_GENERAL_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_REASSEMBLY_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.ValidationException;

import org.hl7.v3.COPCIN000001UK01Message;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.jdbi.v3.core.ConnectionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.pss.translator.exception.AttachmentLogException;
import uk.nhs.adaptors.pss.translator.exception.AttachmentNotFoundException;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.exception.ExternalAttachmentProcessingException;
import uk.nhs.adaptors.pss.translator.exception.InlineAttachmentProcessingException;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.EbxmlReference;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.service.AttachmentHandlerService;
import uk.nhs.adaptors.pss.translator.service.FailedProcessHandlingService;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.service.InboundMessageMergingService;
import uk.nhs.adaptors.pss.translator.service.NackAckPrepInterface;
import uk.nhs.adaptors.pss.translator.service.XPathService;
import uk.nhs.adaptors.pss.translator.storage.StorageException;
import uk.nhs.adaptors.pss.translator.util.InboundMessageUtil;
import uk.nhs.adaptors.pss.translator.util.OutboundMessageUtil;
import uk.nhs.adaptors.pss.translator.util.XmlParseUtilService;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class COPCMessageHandler {

    private static final String MESSAGE_ID_PATH = "/Envelope/Header/MessageHeader/MessageData/MessageId";

    private final MigrationStatusLogService migrationStatusLogService;
    private final PatientMigrationRequestDao migrationRequestDao;
    private final NackAckPrepInterface nackAckPreparationService;
    private final PatientAttachmentLogService patientAttachmentLogService;
    private final AttachmentHandlerService attachmentHandlerService;
    private final InboundMessageMergingService inboundMessageMergingService;
    private final XPathService xPathService;
    private final XmlParseUtilService xmlParseUtilService;
    private final FailedProcessHandlingService failedProcessHandlingService;
    private final OutboundMessageUtil outboundMessageUtil;
    private final InboundMessageUtil inboundMessageUtil;
    private final SendNACKMessageHandler sendNACKMessageHandler;

    private final IdGeneratorService idGeneratorService;

    public void handleMessage(InboundMessage inboundMessage, String conversationId)
            throws JAXBException, InlineAttachmentProcessingException, SAXException, AttachmentLogException,
                AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        COPCIN000001UK01Message payload = unmarshallString(inboundMessage.getPayload(), COPCIN000001UK01Message.class);

        if (failedProcessHandlingService.hasProcessFailed(conversationId)) {
            failedProcessHandlingService.handleFailedProcess(payload, conversationId);
            return;
        }

        PatientMigrationRequest migrationRequest = migrationRequestDao.getMigrationRequest(conversationId);
        migrationStatusLogService.addMigrationStatusLog(COPC_MESSAGE_RECEIVED, conversationId, null, null);

        try {
            Document ebXmlDocument = getEbXmlDocument(inboundMessage);
            String messageId = xPathService.getNodeValue(ebXmlDocument, MESSAGE_ID_PATH);
            PatientAttachmentLog patientAttachmentLog = patientAttachmentLogService.findAttachmentLog(messageId, conversationId);
            migrationStatusLogService.addMigrationStatusLog(COPC_MESSAGE_PROCESSING, conversationId, messageId, null);

            // If there is no PatientAttachmentLog for this message then we have received a message out of order
            if (patientAttachmentLog == null) {
                addLogForEarlyFragmentAndStore(inboundMessage, conversationId, payload, ebXmlDocument, migrationRequest.getId());
            } else {
                if (isManifestMessage(inboundMessage.getAttachments(), inboundMessage.getExternalAttachments())) {
                    extractFragmentsAndLog(migrationRequest, patientAttachmentLog, conversationId, inboundMessage);
                } else {
                    var inlineAttachments = inboundMessage.getAttachments();

                    if (!inlineAttachments.isEmpty()) {
                        // we are expecting inline attachments to only have one attachment in the storeCOPCAttachment method below
                        // so use isBase64 flag of the first inline attachment
                        patientAttachmentLog.setIsBase64(Boolean.valueOf(inlineAttachments.getFirst().getIsBase64()));
                    }

                    storeCOPCAttachment(patientAttachmentLog, inboundMessage, conversationId);
                    patientAttachmentLog.setUploaded(true);

                    var size = (Integer) inboundMessage.getAttachments()
                        .stream()
                        .mapToInt(a -> a.getPayload().length()).sum();

                    patientAttachmentLog.setPostProcessedLengthNum(size);
                    patientAttachmentLogService.updateAttachmentLog(patientAttachmentLog, conversationId);
                }
            }

            checkAndMergeFileParts(inboundMessage, conversationId);
            nackAckPreparationService.sendAckMessage(payload, conversationId, migrationRequest.getLosingPracticeOdsCode());

            // merge and uncompress large EHR message
            if (inboundMessageMergingService.canMergeCompleteBundle(conversationId)) {
                inboundMessageMergingService.mergeAndBundleMessage(conversationId);
            }

        } catch (WebClientRequestException | ConnectionException | MhsServerErrorException e) {
            throw e;
        } catch (ParseException | ValidationException | SAXException e) {
            LOGGER.error("COPC_IN000001UK01 validation / parsing  error", e);
            nackAckPreparationService.sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, payload, conversationId);
            failMigration(conversationId, UNEXPECTED_CONDITION);

        } catch (InlineAttachmentProcessingException e) {
            LOGGER.error("Large messaging attachment processing error", e);
            nackAckPreparationService.sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, payload, conversationId);

            if (e.getCause() instanceof StorageException) {
                failMigration(conversationId, UNEXPECTED_CONDITION);
            } else {
                failMigration(conversationId, LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED);
                migrationStatusLogService.addMigrationStatusLog(LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getMigrationStatus(),
                                                                conversationId,
                                                                null,
                                                                LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
            }

        } catch (ExternalAttachmentProcessingException e) {
            LOGGER.error("Unable to merge attachment", e);
            nackAckPreparationService.sendNackMessage(LARGE_MESSAGE_REASSEMBLY_FAILURE, payload, conversationId);

            failMigration(conversationId, LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED);

        } catch (Exception e) {
            LOGGER.error("Unexpected exception processing COPC_IN000001UK01 message", e);
            nackAckPreparationService.sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, payload, conversationId);
            failMigration(conversationId, UNEXPECTED_CONDITION);
        }
    }

    public void checkAndMergeFileParts(InboundMessage inboundMessage, String conversationId)
        throws SAXException, AttachmentLogException, ValidationException,
        InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        Document ebXmlDocument = xPathService.parseDocumentFromXml(inboundMessage.getEbXML());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, MESSAGE_ID_PATH);
        var currentAttachmentLog = patientAttachmentLogService.findAttachmentLog(inboundMessageId, conversationId);

        if (currentAttachmentLog == null) {
            throw new AttachmentLogException("Given COPC message is missing an attachment log");
        }

        var conversationAttachmentLogs = patientAttachmentLogService.findAttachmentLogs(conversationId);
        // if a message has arrived early, it will not have a parent ID, so we can cancel early.
        // However, an index created by the ehr message will also have no parent id
        // Logic below manages that case to make sure index COPC messages trigger the merge check
        var indexMidReference = currentAttachmentLog.getMid();
        if (currentAttachmentLog.getParentMid() == null) {
            // it could also be an index file following design changes, check to see if it has any child attachments before returning
            var childFragments = conversationAttachmentLogs.stream()
                .filter(log -> (log.getParentMid() != null) && log.getParentMid().equals(indexMidReference))
                .toList();

            // if there are no child records then we can return
            if (childFragments.isEmpty()) {
                return;
            } else {
                // otherwise let's select the first child fragment to run the process as normal
                currentAttachmentLog = childFragments.getFirst();
            }
        }

        PatientAttachmentLog finalCurrentAttachmentLog = currentAttachmentLog;
        var attachmentLogFragments = conversationAttachmentLogs.stream()
            .sorted(Comparator.comparingInt(PatientAttachmentLog::getOrderNum))
            .filter(log -> (log.getParentMid() != null) && log.getParentMid().equals(finalCurrentAttachmentLog.getParentMid()))
            .toList();

        var parentLogMessageId = attachmentLogFragments.size() == 1
            ? currentAttachmentLog.getMid()
            : currentAttachmentLog.getParentMid();

        attachmentLogFragments = conversationAttachmentLogs.stream()
            .sorted(Comparator.comparingInt(PatientAttachmentLog::getOrderNum))
            .filter(log -> (log.getParentMid() != null) && log.getParentMid().equals(parentLogMessageId))
            .toList();

        var allFragmentsHaveUploaded = attachmentLogFragments.stream()
            .allMatch(PatientAttachmentLog::getUploaded);

        if (allFragmentsHaveUploaded) {

            String payload = attachmentHandlerService
                .buildSingleFileStringFromPatientAttachmentLogs(attachmentLogFragments, conversationId);

            var parentLogFile = conversationAttachmentLogs.stream()
                .filter(log ->  log.getMid().equals(parentLogMessageId))
                .findAny()
                .orElseThrow();

            if (payload != null) {
                parentLogFile.setPostProcessedLengthNum(payload.length());
            }

            boolean parentIsBase64 = determineIfParentIsBase64(parentLogFile, attachmentLogFragments);
            parentLogFile.setIsBase64(parentIsBase64);

            var mergedLargeAttachment = createNewLargeAttachmentInList(parentLogFile, payload);

            attachmentHandlerService.storeAttachments(mergedLargeAttachment, conversationId);

            var updatedLog = PatientAttachmentLog.builder()
                .mid(parentLogFile.getMid())
                .uploaded(true)
                .postProcessedLengthNum(parentLogFile.getPostProcessedLengthNum())
                .isBase64(parentIsBase64)
                .build();

            patientAttachmentLogService.updateAttachmentLog(updatedLog, conversationId);

            attachmentLogFragments.forEach((PatientAttachmentLog log) -> {
                attachmentHandlerService.removeAttachment(log.getFilename(), conversationId);
                patientAttachmentLogService.deleteAttachmentLog(log.getMid(), conversationId);
            });
        }
    }

    private boolean determineIfParentIsBase64(PatientAttachmentLog parentLogFile, List<PatientAttachmentLog> attachmentLogFragments)
        throws ExternalAttachmentProcessingException {
        var containsBase64Fragments = attachmentLogFragments.stream()
            .anyMatch(PatientAttachmentLog::getIsBase64);

        var allFragmentsAreBase64 = attachmentLogFragments.stream()
            .allMatch(PatientAttachmentLog::getIsBase64);

        if (containsBase64Fragments && !allFragmentsAreBase64) {
            throw new ExternalAttachmentProcessingException(
                String.format("Received both encoded and decoded fragments for message %s", parentLogFile.getMid())
            );
        }

        return allFragmentsAreBase64;
    }

    private List<InboundMessage.Attachment> createNewLargeAttachmentInList(PatientAttachmentLog largeFileLog, String payload) {

        var fileDescription = largeFileLog.getFileDescription();

        return Collections.singletonList(
                InboundMessage.Attachment.builder()
                        .payload(payload)
                        .isBase64(largeFileLog.getIsBase64().toString())
                        .contentType(largeFileLog.getContentType())
                        .description(fileDescription)
                        .build()
        );
    }

    private Document getEbXmlDocument(InboundMessage inboundMessage) throws SAXException {
        return xPathService.parseDocumentFromXml(inboundMessage.getEbXML());
    }

    private void addLogForEarlyFragmentAndStore(InboundMessage inboundMessage, String conversationId, COPCIN000001UK01Message payload,
        Document ebXmlDocument, int patientId) throws ValidationException, InlineAttachmentProcessingException {
        String fragmentMid = getFragmentMidId(ebXmlDocument);
        String fileName = getFileNameForFragment(inboundMessage, payload);
        var attachment = inboundMessage.getAttachments().getFirst();

        PatientAttachmentLog fragmentAttachmentLog = buildFragmentAttachmentLog(attachment, fragmentMid,
            createFilenameForFragment(fileName), patientId);

        storeCOPCAttachment(fragmentAttachmentLog, inboundMessage, conversationId);
        fragmentAttachmentLog.setUploaded(true);

        patientAttachmentLogService.addAttachmentLog(fragmentAttachmentLog);
    }

    private void storeCOPCAttachment(PatientAttachmentLog fragmentAttachmentLog, InboundMessage inboundMessage,
        String conversationId)
        throws ValidationException, InlineAttachmentProcessingException {

        if (inboundMessage.getAttachments().isEmpty()) {
            throw new InlineAttachmentProcessingException("COPC message does not contain an inline attachment");
        }

        if (fragmentAttachmentLog.getLargeAttachment() == null || fragmentAttachmentLog.getLargeAttachment()) {

            attachmentHandlerService.storeAttachmentWithoutProcessing(fragmentAttachmentLog.getFilename(),
                inboundMessage.getAttachments().getFirst().getPayload(), conversationId,
                fragmentAttachmentLog.getContentType());
        } else {
            var attachment = attachmentHandlerService.buildInboundAttachmentsFromAttachmentLogs(
                List.of(fragmentAttachmentLog),
                List.of(inboundMessage.getAttachments().getFirst().getPayload()),
                conversationId
            );

            attachmentHandlerService.storeAttachments(attachment, conversationId);
        }
    }

    private boolean isManifestMessage(List<InboundMessage.Attachment> attachments,
        List<InboundMessage.ExternalAttachment> externalAttachments) {
        int attachmentsCount = attachments != null ? attachments.size() : 0;
        int externalAttachmentsCount = externalAttachments != null
            ? externalAttachments.size() : 0;
        return attachmentsCount + externalAttachmentsCount > 1;
    }

    private String getFileNameForFragment(InboundMessage inboundMessage, COPCIN000001UK01Message payload) {
        // confirm filename in payload on future examples
        if (!inboundMessage.getAttachments().getFirst().getDescription().isEmpty()
            && inboundMessage.getAttachments().getFirst().getDescription().contains("Filename")) {
            return XmlParseUtilService.parseFragmentFilename(inboundMessage.getAttachments().getFirst().getDescription());
        } else {
            return retrieveFileNameFromPayload(payload);
        }
    }

    private PatientAttachmentLog buildFragmentAttachmentLog(InboundMessage.Attachment attachment, String fragmentMid, String fileName,
        int patientId) {
        var postProcessedLength = attachment.getPayload().length();
        Boolean isBase64 = Boolean.valueOf(attachment.getIsBase64());

        return PatientAttachmentLog.builder()
            .mid(fragmentMid)
            .filename(fileName)
            .contentType(attachment.getContentType())
            .patientMigrationReqId(patientId)
            .postProcessedLengthNum(postProcessedLength)
            .isBase64(isBase64)
            .build();
    }

    private String retrieveFileNameFromPayload(COPCIN000001UK01Message doc) {
        return doc.getControlActEvent()
            .getSubject()
            .getPayloadInformation()
            .getPertinentInformation()
            .getFirst()
            .getPertinentPayloadBody()
            .getValue()
            .getReference()
            .getValue();
    }

    private String getFragmentMidId(Document ebXmlDocument) {
        return xPathService.getNodeValue(ebXmlDocument, MESSAGE_ID_PATH);
    }

    private void extractFragmentsAndLog(PatientMigrationRequest migrationRequest,
        PatientAttachmentLog parentAttachmentLog, String conversationId, InboundMessage message) throws ParseException, SAXException,
        ValidationException, InlineAttachmentProcessingException {

        List<EbxmlReference> attachmentReferenceDescription = new ArrayList<>(
                xmlParseUtilService.getEbxmlAttachmentsData(message));

        // first item is always the message payload reference so skip it
        for (var index = 1; index < attachmentReferenceDescription.size(); index++) {

            var payloadReference = attachmentReferenceDescription.get(index);
            var descriptionString = "";
            var messageId = "";
            var fileUpload = false;
            boolean isBase64 = true;
            String filename;

            // in this instance there should only ever be one CID on a fragment index file
            if (payloadReference.getHref().contains("cid:")) {
                // EMIS does not use unique IDs for cid references, so we have to generate our own
                messageId = "ADAPTOR_GENERATED_" + idGeneratorService.generateUuid().toUpperCase();
                descriptionString = message.getAttachments().getFirst().getDescription();
                filename = createFilenameForFragment(XmlParseUtilService.parseFragmentFilename(descriptionString));
                isBase64 = Boolean.parseBoolean(message.getAttachments().getFirst().getIsBase64());

                // upload the file
                attachmentHandlerService.storeAttachmentWithoutProcessing(
                    filename,
                    message.getAttachments().getFirst().getPayload(),
                    conversationId,
                    message.getAttachments().getFirst().getContentType()
                );
                fileUpload = true;
            } else {
                var localMessageId = payloadReference.getHref().substring(payloadReference.getHref().indexOf("mid:") + "mid:".length());
                messageId = localMessageId;

                var externalAttachmentResult = message.getExternalAttachments()
                    .stream()
                    .filter(attachment -> attachment.getMessageId().equals(localMessageId))
                    .findFirst();

                if (externalAttachmentResult.isEmpty()) {
                    throw new ValidationException("External Attachment in payload header does not match a received External Attachment ID");
                }

                var externalAttachment = externalAttachmentResult.get();
                descriptionString = externalAttachment.getDescription();
                filename = createFilenameForFragment(XmlParseUtilService.parseFragmentFilename(descriptionString));
            }

            PatientAttachmentLog fragmentLog = patientAttachmentLogService.findAttachmentLog(messageId, conversationId);

            if (fragmentLog != null) {
                updateFragmentLog(fragmentLog, parentAttachmentLog, descriptionString, index - 1, parentAttachmentLog.getLargeAttachment());
                patientAttachmentLogService.updateAttachmentLog(fragmentLog, conversationId);
            } else {
                PatientAttachmentLog newFragmentLog = buildPatientAttachmentLog(
                    messageId,
                    descriptionString,
                    migrationRequest.getId(),
                    index - 1,
                    fileUpload,
                    filename,
                    parentAttachmentLog
                );

                if (fileUpload) {
                    newFragmentLog.setIsBase64(isBase64);
                }

                patientAttachmentLogService.addAttachmentLog(newFragmentLog);
            }
        }
    }

    private void updateFragmentLog(PatientAttachmentLog childLog, PatientAttachmentLog parentLog, String descriptionString,
                                   int orderNum, Boolean isLargeAttachment) throws ParseException {
        childLog.setParentMid(parentLog.getMid());
        childLog.setCompressed(XmlParseUtilService.parseCompressed(descriptionString));
        childLog.setLargeAttachment(isLargeAttachment);
        childLog.setSkeleton(parentLog.getSkeleton());
        childLog.setOriginalBase64(XmlParseUtilService.parseOriginalBase64(descriptionString));
        childLog.setOrderNum(orderNum);
    }

    private PatientAttachmentLog buildPatientAttachmentLog(String mid, String description, Integer patientId,
        Integer attachmentOrder, boolean uploaded, String filename, PatientAttachmentLog parentAttachmentLog) throws ParseException {

        return PatientAttachmentLog.builder()
            .mid(mid)
            .filename(filename)
            .parentMid(parentAttachmentLog.getMid())
            .patientMigrationReqId(patientId)
            .contentType(XmlParseUtilService.parseContentType(description))
            .compressed(XmlParseUtilService.parseCompressed(description))
            .largeAttachment(parentAttachmentLog.getLargeAttachment())
            .originalBase64(XmlParseUtilService.parseOriginalBase64(description))
            .skeleton(false)
            .uploaded(uploaded)
            .orderNum(attachmentOrder)
            .build();
    }

    private void failMigration(String conversationId, NACKReason reason) throws JsonProcessingException, JAXBException {
        PatientMigrationRequest migrationRequest = migrationRequestDao.getMigrationRequest(conversationId);

        InboundMessage inboundMessage = inboundMessageUtil.readMessage(migrationRequest.getInboundMessage());
        RCMRIN030000UKMessage ehrExtract = unmarshallString(inboundMessage.getPayload(), RCMRIN030000UKMessage.class);
        String ehrExtractMessageId = outboundMessageUtil.parseMessageRef(ehrExtract);

        NACKMessageData messageData = NACKMessageData
            .builder()
            .nackCode(reason.getCode())
            .fromAsid(outboundMessageUtil.parseFromAsid(ehrExtract))
            .toAsid(outboundMessageUtil.parseToAsid(ehrExtract))
            .toOdsCode(outboundMessageUtil.parseToOdsCode(ehrExtract))
            .messageRef(ehrExtractMessageId)
            .conversationId(conversationId)
            .build();

        LOGGER.debug("An attachment failed to be processed, failing migration for EHR Extract [{}] with reason code [{}]",
            ehrExtractMessageId, reason.getCode());

        sendNACKMessageHandler.prepareAndSendMessage(messageData);
    }

    /**
     * System one can send fragments with non-unique filenames. We expect the filenames to be unique. This method offers a workaround by
     * prepending the filename with a unique UUID. (Should not be used with parent filenames as it will break the link replacement process)
     *
     * @param filename - the original filename
     * @return String - the original filename prepended with a unique UUID
     */
    private String createFilenameForFragment(String filename) {

        String uniqueId = idGeneratorService.generateUuid().toUpperCase();

        return String.format("%s_%s", uniqueId, filename);
    }
}