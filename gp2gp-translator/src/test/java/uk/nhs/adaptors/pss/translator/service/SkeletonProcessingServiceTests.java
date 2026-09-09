package uk.nhs.adaptors.pss.translator.service;

import static java.util.UUID.randomUUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.EbxmlReference;
import uk.nhs.adaptors.pss.translator.util.XmlParseUtilService;

@ExtendWith(MockitoExtension.class)
class SkeletonProcessingServiceTests {

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
    private AttachmentHandlerService attachmentHandlerService;
    @Mock
    private XmlParseUtilService xmlParseUtilService;
    @Mock
    private MigrationStatusLogService migrationStatusLogService;

    @InjectMocks
    private SkeletonProcessingService skeletonProcessingService;

    @SneakyThrows
    private void prepareNonRCMRMocks(InboundMessage inboundMessage) {
        prepareSkeletonNonRCMRMocks(inboundMessage);
    }

    @SneakyThrows
    private void prepareRCMRMocks(InboundMessage inboundMessage) {
        prepareSkeletonRCMRMocks(inboundMessage);
    }

    private void prepareSkeletonNonRCMRMocks(InboundMessage inboundMessage) throws SAXException, TransformerException {

        var reference = new EbxmlReference("First instance is always a payload", "mid:1", "docId");
        var ebXmlAttachments = Arrays.asList(reference);
        var fileAsBytes = readInboundMessageSkeletonPayloadFromFile().getBytes(StandardCharsets.UTF_8);
        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(fileAsBytes);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any())).thenReturn(ebXmlAttachments);
        when(xPathService.parseDocumentFromXml(any())).thenReturn(ebXmlDocument);
        when(xPathService.getNodes(any(), any())).thenReturn(nodeList);
        when(nodeList.item(0)).thenReturn(node);
        when(ebXmlDocument.getElementsByTagName("*")).thenReturn(nodeList);
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(inboundMessage.getPayload());
        when(node.getOwnerDocument()).thenReturn(ebXmlDocument);
        when(node.getParentNode()).thenReturn(node);
    }

    private void prepareSkeletonRCMRMocks(InboundMessage inboundMessage) throws TransformerException {

        var fileAsBytes = readInboundMessagePayloadFromFile().getBytes(StandardCharsets.UTF_8);
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(inboundMessage.getPayload());
        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(fileAsBytes);
    }

    @Test
    void When_UpdateInboundMessageAttachmentHandlerServiceThrowsIllegalArgumentException_Expect_ThrowsException() {

        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        doThrow(IllegalArgumentException.class).when(attachmentHandlerService).getAttachment(any(), any());

        assertThrows(IllegalArgumentException.class, () ->
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog,
                inboundMessage, migrationRequest.getConversationId()));
    }

    @Test
    void When_UpdateInboundMessageWithSkeleton_Expect_AttachmentIsFetchedByFilenameAndConversationId()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareRCMRMocks(inboundMessage);

        skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);

        verify(attachmentHandlerService).getAttachment(FILENAME, CONVERSATION_ID);
    }

    @Test
    void When_HappyPathWithSkeletonAsRCMRMessage_Expect_ThrowNoErrors() throws TransformerException,
        SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareRCMRMocks(inboundMessage);

        skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);
    }

    @Test
    void When_SkeletonAsWholeRCMRMessageHasLeadingWhitespace_Expect_InboundMessagePayloadIsNewRCMRMessage()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();
        var skeletonMessage = "\n  " + readInboundMessagePayloadFromFile();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(skeletonMessage.getBytes(StandardCharsets.UTF_8));
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(readInboundMessagePayloadFromFile());

        var newInboundMessage =
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);

        assertTrue(newInboundMessage.getPayload().contains("<RCMR_IN030000UK06"));
    }

    @Test
    void When_NormalizeSkeletonXmlCalledWithNull_Expect_ReturnsNull() throws Exception {
        var method = SkeletonProcessingService.class.getDeclaredMethod("normalizeSkeletonXml", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(skeletonProcessingService, new Object[] {null}));
    }

    @Test
    void When_NormalizeSkeletonXmlCalledWithBlankString_Expect_ReturnsOriginalBlankString() throws Exception {
        var method = SkeletonProcessingService.class.getDeclaredMethod("normalizeSkeletonXml", String.class);
        method.setAccessible(true);

        var blankInput = " \n\t ";
        assertEquals(blankInput, method.invoke(skeletonProcessingService, blankInput));
    }

    @Test
    void When_IsEntireRcmrSkeletonCalledWithNonRcmrPayload_Expect_ReturnsFalse() throws Exception {
        var method = SkeletonProcessingService.class.getDeclaredMethod("isEntireRcmrSkeleton", String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(skeletonProcessingService, "<MCCI_IN010000UK13>"));
    }

    @Test
    void When_IsEntireRcmrSkeletonCalledWithUk07Payload_Expect_ReturnsTrue() throws Exception {
        var method = SkeletonProcessingService.class.getDeclaredMethod("isEntireRcmrSkeleton", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(skeletonProcessingService, "<RCMR_IN030000UK07>"));
    }

    @Test
    void When_SkeletonAsWholeUk07Message_Expect_InboundMessagePayloadIsNewRCMRMessage()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();
        var skeletonMessage = "<RCMR_IN030000UK07>" + readInboundMessagePayloadFromFile();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(skeletonMessage.getBytes(StandardCharsets.UTF_8));
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(readInboundMessagePayloadFromFile());

        var newInboundMessage =
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);

        assertTrue(newInboundMessage.getPayload().contains("<RCMR_IN030000UK07"));
    }

    @Test
    void When_NormalizeSkeletonXmlCalledWithXmlDeclaration_Expect_StripsDeclarationBeforeRcmrCheck() throws Exception {
        var normalizeMethod = SkeletonProcessingService.class.getDeclaredMethod("normalizeSkeletonXml", String.class);
        normalizeMethod.setAccessible(true);
        var isRcmrMethod = SkeletonProcessingService.class.getDeclaredMethod("isEntireRcmrSkeleton", String.class);
        isRcmrMethod.setAccessible(true);

        var xmlWithDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<RCMR_IN030000UK06>";
        var normalized = (String) normalizeMethod.invoke(skeletonProcessingService, xmlWithDeclaration);

        assertTrue(normalized.startsWith("<RCMR_IN030000UK06"));
        assertTrue((Boolean) isRcmrMethod.invoke(skeletonProcessingService, normalized));
    }

    @Test
    void When_SkeletonAsWholeRCMRMessageHasBomPrefix_Expect_InboundMessagePayloadIsNewRCMRMessage()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();
        var skeletonMessage = "\uFEFF" + readInboundMessagePayloadFromFile();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(skeletonMessage.getBytes(StandardCharsets.UTF_8));
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(readInboundMessagePayloadFromFile());

        var newInboundMessage =
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);

        assertTrue(newInboundMessage.getPayload().contains("<RCMR_IN030000UK06"));
    }

    @Test
    void When_HappyPathWithSkeletonAsRCMRMessage_Expect_InboundMessagePayloadIsNewRCMRMessage()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareRCMRMocks(inboundMessage);

        var newInboundMessage =
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);

        assertEquals(newInboundMessage.getPayload(), readInboundMessagePayloadFromFile());
    }

    @Test
    void When_HappyPathWithSkeletonAsSectionMessage_Expect_ThrowNoErrors()
        throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareNonRCMRMocks(inboundMessage);

        skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);
    }

    @Test
    void When_SkeletonAsSectionMessage_Expect_ThrowNoErrors() throws TransformerException, SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        prepareNonRCMRMocks(inboundMessage);
        skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog, inboundMessage, CONVERSATION_ID);
    }

    @Test
    void When_SkeletonAsSectionMessageAndEBXMLSkeletonReferenceIsEmpty_Expect_IllegalArgumentException()
        throws SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var reference = new EbxmlReference("First instance is always a payload", "mid:1", "docId");
        var ebXmlAttachments = List.of(reference);
        var fileAsBytes = readInboundMessageSkeletonPayloadFromFile().getBytes(StandardCharsets.UTF_8);
        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(fileAsBytes);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any())).thenReturn(ebXmlAttachments);
        when(xPathService.parseDocumentFromXml(any())).thenReturn(ebXmlDocument);

        var emptyAttachmentsData = new ArrayList<EbxmlReference>();
        when(xmlParseUtilService.getEbxmlAttachmentsData(any())).thenReturn(
            emptyAttachmentsData
        );

        assertThrows(IllegalArgumentException.class, () ->
            skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog,
                inboundMessage, migrationRequest.getConversationId()));
    }

    @Test
    void When_SkeletonAsSectionMessageAndPayloadNodeNotFound_Expect_IllegalArgumentException()
            throws SAXException {
        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var reference = new EbxmlReference("First instance is always a payload", "mid:1", "docId");
        var ebXmlAttachments = List.of(reference);
        var fileAsBytes = readInboundMessageSkeletonPayloadFromFile().getBytes(StandardCharsets.UTF_8);

        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(fileAsBytes);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any())).thenReturn(ebXmlAttachments);
        when(xPathService.parseDocumentFromXml(any())).thenReturn(ebXmlDocument);
        when(xPathService.getNodes(any(), any())).thenReturn(nodeList);
        when(nodeList.item(0)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                skeletonProcessingService.updateInboundMessageWithSkeleton(attachmentLog,
                        inboundMessage, migrationRequest.getConversationId()));
    }

    PatientAttachmentLog createSkeletonPatientAttachmentLog() {
        return
            PatientAttachmentLog.builder()
                    .filename(FILENAME)
                    .mid("1")
                    .orderNum(0)
                    .parentMid("0")
                    .uploaded(true)
                    .largeAttachment(true)
                    .originalBase64(true)
                    .compressed(false)
                    .contentType("text/plain")
                    .lengthNum(0)
                    .skeleton(true)
                    .deleted(false)
                    .patientMigrationReqId(1)
                    .build();
    }

    @Test
    void When_SkeletonDocumentIdHasLeadingUnderscore_Expect_UnderscoreIsStrippedBeforeXPathQuery()
            throws SAXException, TransformerException {

        var inboundMessage = new InboundMessage();
        var attachmentLog = createSkeletonPatientAttachmentLog();

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());

        var reference = new EbxmlReference(
                "First instance is always a payload",
                "mid:1",
                "_C3866E77-41E2-4593-A133-AB622F54684F"
        );
        var ebXmlAttachments = List.of(reference);
        var fileAsBytes = readInboundMessageSkeletonPayloadFromFile().getBytes(StandardCharsets.UTF_8);

        when(attachmentHandlerService.getAttachment(any(), any())).thenReturn(fileAsBytes);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any())).thenReturn(ebXmlAttachments);
        when(xPathService.parseDocumentFromXml(any())).thenReturn(ebXmlDocument);
        when(xPathService.getNodes(any(), any())).thenReturn(nodeList);
        when(nodeList.item(0)).thenReturn(node);
        when(ebXmlDocument.getElementsByTagName("*")).thenReturn(nodeList);
        when(xmlParseUtilService.getStringFromDocument(any())).thenReturn(inboundMessage.getPayload());
        when(node.getOwnerDocument()).thenReturn(ebXmlDocument);
        when(node.getParentNode()).thenReturn(node);

        skeletonProcessingService.updateInboundMessageWithSkeleton(
                attachmentLog, inboundMessage, CONVERSATION_ID);

        var expectedDocumentId = "C3866E77-41E2-4593-A133-AB622F54684F";
        var expectedXPath = "//*/@*[.='" + expectedDocumentId + "']/parent::*/parent::*";

        verify(xPathService).getNodes(any(), eq(expectedXPath));
    }

    @SneakyThrows
    private String readInboundMessagePayloadFromFile() {
        return readResourceAsString("/xml/inbound_message_payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readInboundMessageSkeletonPayloadFromFile() {
        return readResourceAsString("/xml/inbound_message_skeleton_section_payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readInboundMessageEbXmlFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06_LARGE_MSG/ebxml.xml");
    }
}
