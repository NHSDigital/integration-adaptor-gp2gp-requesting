package uk.nhs.adaptors.pss.translator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import org.hl7.v3.RCMRIN030000UK06Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
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
import uk.nhs.adaptors.pss.translator.model.EbxmlReference;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.storage.StorageManagerService;
import uk.nhs.adaptors.pss.translator.util.XmlParseUtilService;
import uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil;

import javax.xml.bind.JAXBException;
import javax.xml.bind.ValidationException;
import javax.xml.transform.TransformerException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

@ExtendWith(MockitoExtension.class)
public class InboundMessageMergingServiceTests {

    private static final String CONVERSATION_ID = randomUUID().toString();
    private static final String NHS_NUMBER = "1111";
    private static final String FILENAME = "test_main.txt";

    @Mock
    private NodeList nodeList;
    @Mock
    private Node node;
    @Mock
    private PatientAttachmentLogService patientAttachmentLogService;
    @Mock
    private PatientMigrationRequestDao migrationRequestDao;
    @Mock
    private PatientMigrationRequest migrationRequest;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private BundleMapperService bundleMapperService;
    @Mock
    private XPathService xPathService;
    @Mock
    private Document ebXmlDocument;
    @Mock
    private InboundMessage inboundMessageMock;
    @Mock
    private Document document;
    @Mock
    private NackAckPreparationService nackAckPreparationService;
    @Mock
    private AttachmentHandlerService attachmentHandlerService;
    @Mock
    private AttachmentReferenceUpdaterService attachmentReferenceUpdaterService;
    @Mock
    private XmlParseUtilService xmlParseUtilService;
    @Mock
    private MigrationStatusLogService migrationStatusLogService;
    @Mock
    private FhirParser fhirParser;
    @Mock
    private StorageManagerService storageManagerService;
    @Mock
    private XmlUnmarshallUtil xmlUnmarshallUtil;
    @Mock
    private SkeletonProcessingService skeletonProcessingService;

    @InjectMocks
    private InboundMessageMergingService inboundMessageMergingService;

    @SneakyThrows
    private void prepareMocks(InboundMessage inboundMessage, ArrayList<PatientAttachmentLog> attachments) {
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(attachmentReferenceUpdaterService.updateReferenceToAttachment(any(), any(), any())).thenReturn(inboundMessage.getPayload());

        if (attachments.get(0).getSkeleton()) {
            prepareSkeletonMocks(inboundMessage);
        }
    }

    private void prepareSkeletonMocks(InboundMessage inboundMessage) throws SAXException, TransformerException {

        var reference = new EbxmlReference("First instance is always a payload", "mid:1", "docId");
        var ebXmlAttachments = Arrays.asList(reference);
        var fileAsBytes = readInboundMessagePayloadFromFile().getBytes(StandardCharsets.UTF_8);

//        when(attachmentHandlerService.getAttachment(FILENAME, CONVERSATION_ID)).thenReturn(fileAsBytes);
//        when(xmlParseUtilService.getEbxmlAttachmentsData(inboundMessage)).thenReturn(ebXmlAttachments);
//        when(xPathService.parseDocumentFromXml(any())).thenReturn(ebXmlDocument);
//        when(xPathService.getNodes(any(), any())).thenReturn(nodeList);
//        when(nodeList.item(0)).thenReturn(node);
//        when(ebXmlDocument.getElementsByTagName("*")).thenReturn(nodeList);
//        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(inboundMessage.getPayload());
//        when(node.getOwnerDocument()).thenReturn(ebXmlDocument);
//        when(node.getParentNode()).thenReturn(node);

    }

    @Test
    public void When_HappyPathWithSkeleton_Expect_ThrowNoErrors() throws JAXBException, JsonProcessingException, TransformerException,
        SAXException {
        var inboundMessage = new InboundMessage();
        var attachments = createPatientAttachmentList(true, true);

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareMocks(inboundMessage, attachments);
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);

        verify(nackAckPreparationService, never()).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
        verify(migrationStatusLogService, times(1)).updatePatientMigrationRequestAndAddMigrationStatusLog(any(), any(), any(), any());
    }

    @Test
    public void When_HappyPathWithSkeleton_Expect_SkeletonProcessingServiceUpdateInboundMessageWithSkeletonToBeCalledOnce()
        throws JAXBException, JsonProcessingException, TransformerException,
        SAXException {
        var inboundMessage = new InboundMessage();
        var attachments = createPatientAttachmentList(true, true);

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareMocks(inboundMessage, attachments);
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);

        verify(skeletonProcessingService, times(1)).updateInboundMessageWithSkeleton(any(), any(), any());
 }

    @Test
    public void When_HappyPathNoSkeleton_Expect_NotToGetAttachmentFromService() throws JAXBException, JsonProcessingException,
        TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachments = createPatientAttachmentList(true, false);

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareMocks(inboundMessage, attachments);

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);

        verify(nackAckPreparationService, never()).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
        verify(attachmentHandlerService, never()).getAttachment(any(), any());
        verify(skeletonProcessingService, times(0)).updateInboundMessageWithSkeleton(any(), any(), any());
        verify(migrationStatusLogService, times(1)).updatePatientMigrationRequestAndAddMigrationStatusLog(any(), any(), any(), any());
    }

    @Test
    public void When_UpdateInboundMessageWithSkeletonThrowsSAXException_Expect_SendNack() throws JAXBException,
        JsonProcessingException, SAXException, TransformerException, AttachmentNotFoundException, InlineAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        var attachments = createPatientAttachmentList(true, true);

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);

        doThrow(SAXException.class).when(skeletonProcessingService).updateInboundMessageWithSkeleton(any(), any(), any());
        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1)).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }


    @Test
    public void When_MergeAndBundleMessageThrowsValidationException_Expect_SendNack() throws JAXBException,
        JsonProcessingException, SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        doThrow(ValidationException.class).when(attachmentReferenceUpdaterService).updateReferenceToAttachment(any(), any(), any());

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1)).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_MergeAndBundleMessageThrowsInlineAttachmentProcessingException_Expect_SendNack() throws JAXBException,
        JsonProcessingException, SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        doThrow(InlineAttachmentProcessingException.class)
                .when(attachmentReferenceUpdaterService)
                .updateReferenceToAttachment(any(), any(), any());

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1))
                .sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_MergeAndBundleMessageThrowsAttachmentNotFoundException_Expect_SendNack() throws JAXBException, JsonProcessingException,
        SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        doThrow(AttachmentNotFoundException.class).when(attachmentReferenceUpdaterService).updateReferenceToAttachment(any(), any(), any());

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1))
                .sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_UpdateInboundMessageWithSkeletonThrowsTransformerException_Expect_SendNack() throws JAXBException, JsonProcessingException,
        SAXException, TransformerException, AttachmentNotFoundException, InlineAttachmentProcessingException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);

        doThrow(TransformerException.class).when(skeletonProcessingService).updateInboundMessageWithSkeleton(any(), any(), any());

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1))
                .sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_MergeAndBundleMessageThrowsJAXBException_Expect_SendNack() throws JAXBException, JsonProcessingException,
        SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(attachmentReferenceUpdaterService.updateReferenceToAttachment(any(), any(), any())).thenReturn("");
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1))
                .sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_MergeAndBundleMessageThrowsBundleMappingException_Expect_SendNack() throws JAXBException, JsonProcessingException,
        SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, BundleMappingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(attachmentReferenceUpdaterService.updateReferenceToAttachment(any(), any(), any())).thenReturn(inboundMessage.getPayload());
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        doThrow(BundleMappingException.class).when(bundleMapperService).mapToBundle(any(RCMRIN030000UK06Message.class), any());

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1))
                .sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_MergeAndBundleMessageThrowsJsonProcessingException_Expect_SendNack() throws JAXBException, JsonProcessingException,
        SAXException, AttachmentNotFoundException, InlineAttachmentProcessingException, TransformerException {
        var inboundMessage = new InboundMessage();

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var attachments = createPatientAttachmentList(true, true);
        var inboundMessageAsString = objectMapper.writeValueAsString(inboundMessage);
        var patientMigrationRequest = PatientMigrationRequest
                .builder()
                .inboundMessage(inboundMessageAsString)
                .build();

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);
        when(migrationRequestDao.getMigrationRequest(any())).thenReturn(patientMigrationRequest);
        when(objectMapper.readValue(inboundMessageAsString, InboundMessage.class)).thenReturn(inboundMessage);
        when(attachmentReferenceUpdaterService.updateReferenceToAttachment(any(), any(), any())).thenReturn(inboundMessage.getPayload());
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);

        doThrow(JsonProcessingException.class).when(objectMapper).writeValueAsString(any(InboundMessage.class));

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);
        verify(nackAckPreparationService, times(1)).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }

    @Test
    public void When_NotAllUploadsComplete_CanMergeCompleteBundle_Expect_ReturnFalse() throws JAXBException {
        var attachmentLogs = createPatientAttachmentList(false, false);

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachmentLogs);

        var result = inboundMessageMergingService.canMergeCompleteBundle(CONVERSATION_ID);

        assertFalse(result);
    }

    @Test
    public void When_AllUploadsComplete_CanMergeCompleteBundle_Expect_ReturnTrue() throws JAXBException {
        var attachmentLogs = createPatientAttachmentList(true, true);
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachmentLogs);

        var result = inboundMessageMergingService.canMergeCompleteBundle(CONVERSATION_ID);

        assertTrue(result);
    }

    @Test
    public void When_CanMergeCompleteBundleHasNullOrEmptyParams_Expect_ThrowIllegalStateException() {
        var conversationId = "";
        assertThrows(ValidationException.class, () -> inboundMessageMergingService.canMergeCompleteBundle(conversationId));
    }

    @Test
    public void When_MergeAndBundleMessageHasNullOrEmptyParams_Expect_ThrowIllegalStateException() {
        var conversationId = "";
        assertThrows(ValidationException.class, () -> inboundMessageMergingService.mergeAndBundleMessage(conversationId));
    }

    @Test
    public void When_AttachmentsPresent_Expect_AttachmentReferenceUpdated()
        throws AttachmentNotFoundException, JAXBException, InlineAttachmentProcessingException, JsonProcessingException,
        TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachments = createPatientAttachmentList(true, false);

        inboundMessage.setPayload("payload");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareMocks(inboundMessage, attachments);

        // no need for skeleton here
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID)).thenReturn(attachments);

        inboundMessageMergingService.mergeAndBundleMessage(CONVERSATION_ID);

        verify(attachmentReferenceUpdaterService).updateReferenceToAttachment(any(), any(), any());
        verify(nackAckPreparationService, never()).sendNackMessage(any(NACKReason.class), any(RCMRIN030000UK06Message.class), any());
    }


    private ArrayList<PatientAttachmentLog> createPatientAttachmentList(Boolean isParentUploaded, Boolean isSkeleton) {
        var patientAttachmentLogs = new ArrayList<PatientAttachmentLog>();
        patientAttachmentLogs.add(
                PatientAttachmentLog.builder()
                        .filename(FILENAME)
                        .mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(isParentUploaded)
                        .largeAttachment(true)
                        .base64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(isSkeleton)
                        .deleted(false)
                        .patientMigrationReqId(1)
                        .build()
        );
        return patientAttachmentLogs;
    }

    @SneakyThrows
    private String readInboundMessagePayloadFromFile() {
        return readResourceAsString("/xml/inbound_message_payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readInboundMessageEbXmlFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06_LARGE_MSG/ebxml.xml");
    }
}
