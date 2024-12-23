package uk.nhs.adaptors.pss.translator.task;

import static java.util.UUID.randomUUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_RECEIVED;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_GENERAL_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.ValidationException;

import org.apache.commons.lang3.StringUtils;
import org.hl7.v3.COPCIN000001UK01Message;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.jdbi.v3.core.ConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.SneakyThrows;
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
import uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil;

@SuppressWarnings("InstantiationOfUtilityClass")
@ExtendWith(MockitoExtension.class)
class COPCMessageHandlerTest {

    private static final String CONVERSATION_ID = randomUUID().toString();
    private static final String LOSING_ODE_CODE = "G543";
    private static final String WINNING_ODE_CODE = "B943";
    private static final String MESSAGE_ID = "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1";
    private static final String NHS_NUMBER = "123456";
    private static final Integer DATA_AMOUNT = 3;

    private static final String ATTACH = "RESPONSE";
    private static final Integer INVALID_ATTACH_LENGTH = 9;

    private static final Integer ATTACH_LENGTH = 8;

    private static final String TEST_ID = "TEST-ID";

    @Mock
    private PatientMigrationRequestDao migrationRequestDao;
    @Mock
    private PatientAttachmentLogService patientAttachmentLogService;
    @Mock
    private MigrationStatusLogService migrationStatusLogService;
    @Mock
    private AttachmentHandlerService attachmentHandlerService;
    @Mock
    private XPathService xPathService;
    @Mock
    private SendACKMessageHandler sendACKMessageHandler;
    @Mock
    private InboundMessageMergingService inboundMessageMergingService;
    @Mock
    private XmlParseUtilService xmlParseUtilService;

    @Mock
    private FailedProcessHandlingService failedProcessHandlingService;

    private Document ebXmlDocument;
    @Mock
    private OutboundMessageUtil outboundMessageUtil;
    @Mock
    private InboundMessageUtil inboundMessageUtil;
    @Mock
    private SendNACKMessageHandler sendNACKMessageHandler;
    @Mock
    private IdGeneratorService idGeneratorService;
    @InjectMocks
    private COPCMessageHandler copcMessageHandler;
    @Captor
    private ArgumentCaptor<PatientAttachmentLog> patientLogCaptor;
    @Captor
    private ArgumentCaptor<String> filenameCaptor;
    @Captor
    private ArgumentCaptor<String> payloadCaptor;
    @Captor
    private ArgumentCaptor<String> conversationIdCaptor;
    @Captor
    private ArgumentCaptor<Integer> fileLengthCaptor;
    @Captor
    private ArgumentCaptor<String> contentTypeCaptor;

    @Mock
    private NackAckPrepInterface nackAckPreparationServiceMock;

    @Captor
    private ArgumentCaptor<NACKMessageData> nackMessageDataCaptor;

    @Mock
    private COPCIN000001UK01Message mockCOPCMessage;
    @Mock
    private RCMRIN030000UKMessage mockEhrExtract;
    @Mock
    private InboundMessage mockInboundMessage;

    @Test
    public void When_CIDFragmentPartIsReceivedBeforeFragmentIndex_Expect_PartialLogToBeCreated()
        throws JAXBException, InlineAttachmentProcessingException, SAXException, AttachmentLogException,
               AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        // ARRANGE
        var messageId = "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1";
        when(patientAttachmentLogService.findAttachmentLog(messageId, CONVERSATION_ID)).thenReturn(null)
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));
        when(patientAttachmentLogService.findAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));
        when(idGeneratorService.generateUuid()).thenReturn(TEST_ID);

        InboundMessage message = new InboundMessage();
        prepareFragmentMocks(message);

        // ACT
        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        // ASSERT
        verify(patientAttachmentLogService).addAttachmentLog(patientLogCaptor.capture());

        PatientAttachmentLog actual = patientLogCaptor.getAllValues().getFirst();

        assertThat(actual.getUploaded()).isTrue();
        assertEquals("047C22B4-613F-47D3-9A72-44A1758464FB", actual.getMid());
        assertEquals(TEST_ID + "_E39E79A2-FA96-48FF-9373-7BBCB9D036E7_1.messageattachment", actual.getFilename());
        assertEquals("xml/text", actual.getContentType());
        assertEquals(1, actual.getPatientMigrationReqId());
    }

    @Test
    public void When_CIDFragmentPartIsReceivedBeforeFragmentIndex_Expect_StoreAttachmentOnly()
        throws JAXBException, InlineAttachmentProcessingException, SAXException,
               AttachmentLogException, AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        InboundMessage message = new InboundMessage();
        prepareFragmentMocks(message);
        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(null)
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));

        when(patientAttachmentLogService.findAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));

        // ACT
        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        // Assert
        verify(attachmentHandlerService).storeAttachmentWithoutProcessing(any(), any(), any(), any());
    }

    @Test
    public void When_MIDFragmentRecordHasAnAttachmentLog_Expect_AttachmentLogToBeUpdated()
        throws JAXBException, InlineAttachmentProcessingException, SAXException, AttachmentLogException,
               AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        var parentMid = "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1";
        var childMid = "28B31-4245-4AFC-8DA2-8A40623A5101";
        InboundMessage message = new InboundMessage();
        prepareFragmentIndexMocks(message);

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog(parentMid, null, true));
        when(patientAttachmentLogService.findAttachmentLog(childMid, CONVERSATION_ID))
            .thenReturn(buildPartialPatientAttachmentLog(childMid, "text/plain"));
        when(patientAttachmentLogService.findAttachmentLog("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));

        EbxmlReference reference = new EbxmlReference("First instance is always a payload", "mid:xxxx-xxxx-xxxx-xxxx", "docId");
        EbxmlReference reference2 = new EbxmlReference("desc", "mid:28B31-4245-4AFC-8DA2-8A40623A5101", "docId");
        List<EbxmlReference> attachmentReferenceDescription = new ArrayList<>();
        attachmentReferenceDescription.add(reference);
        attachmentReferenceDescription.add(reference2);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any()))
            .thenReturn(attachmentReferenceDescription);
        when(idGeneratorService.generateUuid()).thenReturn(TEST_ID);

        // ACT
        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        // ASSERT
        verify(patientAttachmentLogService).updateAttachmentLog(patientLogCaptor.capture(), conversationIdCaptor.capture());

        PatientAttachmentLog actual = patientLogCaptor.getValue();
        assertThat(actual.getOriginalBase64()).isTrue();
        assertThat(actual.getLargeAttachment()).isTrue();
        assertThat(actual.getCompressed()).isFalse();
        assertEquals(0, actual.getOrderNum());
        assertEquals("047C22B4-613F-47D3-9A72-44A1758464FB", actual.getParentMid());
        assertEquals(CONVERSATION_ID, conversationIdCaptor.getValue());
    }

    @Test
    public void When_MIDFragmentRecordDoesNotHaveAnAttachmentLog_Expect_AttachmentLogToBeCreated()
        throws JAXBException, InlineAttachmentProcessingException, SAXException, AttachmentLogException,
               AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        var childMid = "28B31-4245-4AFC-8DA2-8A40623A5101";
        InboundMessage message = new InboundMessage();
        prepareFragmentIndexMocks(message);

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", null, true));
        when(patientAttachmentLogService.findAttachmentLog(childMid, CONVERSATION_ID))
            .thenReturn(null);
        when(patientAttachmentLogService.findAttachmentLog("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));
        when(idGeneratorService.generateUuid()).thenReturn(TEST_ID);

        EbxmlReference reference = new EbxmlReference("First instance is always a payload", "mid:xxxx-xxxx-xxxx-xxxx", "docId");
        EbxmlReference reference2 = new EbxmlReference("desc", "mid:28B31-4245-4AFC-8DA2-8A40623A5101", "docId");
        List<EbxmlReference> attachmentReferenceDescription = new ArrayList<>();
        attachmentReferenceDescription.add(reference);
        attachmentReferenceDescription.add(reference2);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any()))
            .thenReturn(attachmentReferenceDescription);

        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        verify(patientAttachmentLogService).addAttachmentLog(patientLogCaptor.capture());
        PatientAttachmentLog actual = patientLogCaptor.getValue();

        assertEquals(childMid, actual.getMid());
        assertEquals(TEST_ID + "_E39E79A2-FA96-48FF-9373-7BBCB9D036E7_0.messageattachment", actual.getFilename());
        assertEquals("047C22B4-613F-47D3-9A72-44A1758464FB", actual.getParentMid());
        assertEquals(1, actual.getPatientMigrationReqId());
        assertEquals("text/plain", actual.getContentType());
        assertThat(actual.getCompressed()).isFalse();
        assertThat(actual.getLargeAttachment()).isTrue();
        assertThat(actual.getOriginalBase64()).isTrue();
        assertEquals(0, actual.getOrderNum());
    }

    @Test
    public void When_FragmentIndexIsRecievedWithCIDAndMIDParts_Expect_CIDMessageToBeProcessed() throws JAXBException,
                                                                                                       InlineAttachmentProcessingException,
                                                                                                       SAXException,
                                                                                                       AttachmentLogException,
                                                                                                       AttachmentNotFoundException,
                                                                                                       BundleMappingException,
                                                                                                       JsonProcessingException {

        var childMid = "28B31-4245-4AFC-8DA2-8A40623A5101";
        var generatedCid = "ADAPTOR_GENERATED_TEST-ID";
        InboundMessage message = new InboundMessage();
        prepareFragmentIndexWithCidMocks(message);

        when(idGeneratorService.generateUuid()).thenReturn(TEST_ID);

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", null, true));
        when(patientAttachmentLogService.findAttachmentLog(childMid, CONVERSATION_ID))
            .thenReturn(null);
        when(patientAttachmentLogService.findAttachmentLog(generatedCid, CONVERSATION_ID))
            .thenReturn(null);
        when(patientAttachmentLogService.findAttachmentLog("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));

        EbxmlReference reference = new EbxmlReference("First instance is always a payload", "mid:xxxx-xxxx-xxxx-xxxx", "docId");
        EbxmlReference reference1 = new EbxmlReference("desc", "cid:435B1171-31F6-4EF2-AD7F-C7E64EEFF357", "doc1Id");
        EbxmlReference reference2 = new EbxmlReference("desc", "mid:28B31-4245-4AFC-8DA2-8A40623A5101", "doc2Id");
        List<EbxmlReference> attachmentReferenceDescription = new ArrayList<>();
        attachmentReferenceDescription.add(reference);
        attachmentReferenceDescription.add(reference1);
        attachmentReferenceDescription.add(reference2);
        when(xmlParseUtilService.getEbxmlAttachmentsData(any()))
            .thenReturn(attachmentReferenceDescription);

        copcMessageHandler.handleMessage(message, CONVERSATION_ID);
        verify(attachmentHandlerService)
            .storeAttachmentWithoutProcessing(TEST_ID + "_CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1_0.messageattachment",
                                              "ABC Not Required", CONVERSATION_ID, "text/plain");
        verify(patientAttachmentLogService, times(2)).addAttachmentLog(patientLogCaptor.capture());

        PatientAttachmentLog actualCidAttachmentLog = patientLogCaptor.getAllValues().getFirst();
        PatientAttachmentLog actualMidAttachmentLog = patientLogCaptor.getAllValues().get(1);

        assertEquals(generatedCid, actualCidAttachmentLog.getMid());
        assertEquals(childMid, actualMidAttachmentLog.getMid());
    }

    @Test
    public void When_CIDFragmentPartIsReceivedInOrder_Expect_ShouldUploadFile()
        throws JAXBException, InlineAttachmentProcessingException, SAXException,
               AttachmentLogException, AttachmentNotFoundException, BundleMappingException, JsonProcessingException {

        InboundMessage message = new InboundMessage();
        prepareExpectedFragmentMocks(message);

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB",
                                                  "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));

        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        verify(attachmentHandlerService).storeAttachmentWithoutProcessing(filenameCaptor.capture(), payloadCaptor.capture(),
                                                                          conversationIdCaptor.capture(), contentTypeCaptor.capture());

        assertEquals("E39E79A2-FA96-48FF-9373-7BBCB9D036E7.txt", filenameCaptor.getValue());
        assertEquals("This is a payload", payloadCaptor.getValue());
        assertEquals(CONVERSATION_ID, conversationIdCaptor.getValue());
        assertEquals("xml/text", contentTypeCaptor.getValue());
    }

    @Test
    public void When_CIDMessageFileIsProcessed_Expect_AttachmentLogUploadedColumnIsSetToTrue()
        throws JAXBException, InlineAttachmentProcessingException, SAXException, AttachmentLogException,
               AttachmentNotFoundException, BundleMappingException, JsonProcessingException {
        InboundMessage message = new InboundMessage();
        prepareExpectedFragmentMocks(message);

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));

        copcMessageHandler.handleMessage(message, CONVERSATION_ID);

        verify(patientAttachmentLogService).updateAttachmentLog(patientLogCaptor.capture(), conversationIdCaptor.capture());

        PatientAttachmentLog actual = patientLogCaptor.getAllValues().getFirst();
        assertThat(actual.getUploaded()).isTrue();
        assertEquals(CONVERSATION_ID, conversationIdCaptor.getValue());
    }

    @Test
    public void When_CanMergeCompleteBundle_Expect_MergeAndBundle()
        throws AttachmentNotFoundException, JAXBException, BundleMappingException, JsonProcessingException,
               InlineAttachmentProcessingException, SAXException, AttachmentLogException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readCopcInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        prepareMocks();

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
            .thenReturn(inboundMessageId);
        inboundMessage.setExternalAttachments(Arrays.asList(InboundMessage.ExternalAttachment.builder().build()));
        inboundMessage.setAttachments(Arrays.asList(InboundMessage.Attachment.builder().build()));


        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_main.txt")
                            .mid("1")
                            .parentMid("0")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(true, true, DATA_AMOUNT));

        when(inboundMessageMergingService.canMergeCompleteBundle(CONVERSATION_ID)).thenReturn(true);

        copcMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        verify(inboundMessageMergingService, times(1)).mergeAndBundleMessage(CONVERSATION_ID);
    }

    @Test
    public void When_CanMergeCompleteBundle_ReturnsFalse_Expect_MergeAndBundleNotCalled()
        throws AttachmentNotFoundException, JAXBException, BundleMappingException, JsonProcessingException,
               InlineAttachmentProcessingException, SAXException, AttachmentLogException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readCopcInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        prepareMocks();

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
            .thenReturn(inboundMessageId);
        inboundMessage.setExternalAttachments(Arrays.asList(InboundMessage.ExternalAttachment.builder().build()));
        inboundMessage.setAttachments(Arrays.asList(InboundMessage.Attachment.builder().build()));

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_main.txt")
                            .mid("1")
                            .parentMid("0")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(true, true, DATA_AMOUNT));

        when(inboundMessageMergingService.canMergeCompleteBundle(CONVERSATION_ID)).thenReturn(false);

        copcMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        verify(inboundMessageMergingService, never()).mergeAndBundleMessage(CONVERSATION_ID);
    }

    @Test
    public void When_HappyPath_Expect_ThrowNoErrors()
        throws SAXException, ValidationException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2").parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(false, true, DATA_AMOUNT));

        when(attachmentHandlerService.buildSingleFileStringFromPatientAttachmentLogs(any(), any()))
            .thenReturn("test-string");
        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
    }

    @Test
    public void When_CurrentAttachmentLogIsNull_Expect_ThrowError() {
        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());

        when(patientAttachmentLogService.findAttachmentLog(any(), any()))
            .thenReturn(null);

        assertThrows(AttachmentLogException.class, () -> copcMessageHandler.checkAndMergeFileParts(inboundMessage, any()));
    }

    @Test
    public void When_CurrentAttachmentLogExists_Expect_ThrowNoError()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2")
                            .parentMid("1")
                            .patientMigrationReqId(1)
                            .build());
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(false, true, DATA_AMOUNT));

        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
    }

    @Test
    public void When_FragmentsAreMissingOrUploaded_Expect_NotDeleteFragments()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2").parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        var invalidFragment = PatientAttachmentLog.builder()
            .filename("test_frag_3.txt")
            .mid("3")
            .orderNum(0)
            .parentMid("1")
            .uploaded(false)
            .largeAttachment(true)
            .originalBase64(true)
            .compressed(false)
            .contentType("text/plain")
            .lengthNum(0)
            .skeleton(false)
            .patientMigrationReqId(1).build();

        var validAttachmentLogList = createPatientAttachmentList(false, true, DATA_AMOUNT);
        validAttachmentLogList.add(invalidFragment);

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(validAttachmentLogList);

        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);

        verify(attachmentHandlerService, never()).buildSingleFileStringFromPatientAttachmentLogs(any(), any());
    }

    @Test
    public void When_CheckByteCompilationCreatesFileAsExpected_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2")
                            .parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(false, true, DATA_AMOUNT));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(attachmentHandlerService, times(1))
            .buildSingleFileStringFromPatientAttachmentLogs(any(), any());

    }

    @Test
    public void When_EnsureStoreAttachmentsIsCalled_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2")
                            .parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(new ArrayList<>(
                Arrays.asList(
                    PatientAttachmentLog.builder().filename("test_main.txt").mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(false)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_1.txt")
                        .mid("2")
                        .orderNum(0)
                        .parentMid("1")
                        .uploaded(true)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_2.txt")
                        .mid("3")
                        .orderNum(1)
                        .parentMid("1")
                        .uploaded(true)
                        .patientMigrationReqId(1)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .isBase64(true)
                        .skeleton(false).build()
                             )));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(attachmentHandlerService, times(1)).storeAttachments(any(), eq(CONVERSATION_ID));
    }

    @Test
    public void When_UpdateAttachmentLogDoesAsExpected_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(
                PatientAttachmentLog.builder()
                    .filename("test_frag_1.txt")
                    .mid("2")
                    .parentMid("1")
                    .patientMigrationReqId(1)
                    .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(new ArrayList<>(
                Arrays.asList(
                    PatientAttachmentLog.builder().filename("test_main.txt").mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(false)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_1.txt")
                        .mid("2")
                        .orderNum(0)
                        .parentMid("1")
                        .uploaded(true)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_2.txt")
                        .mid("3")
                        .orderNum(1)
                        .parentMid("1")
                        .uploaded(true)
                        .patientMigrationReqId(1)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .isBase64(true)
                        .skeleton(false).build()
                             )));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(patientAttachmentLogService, times(1))
            .updateAttachmentLog(any(), eq(CONVERSATION_ID));
    }

    @Test
    public void When_DeleteAttachmentCalledForEachAttachmentLogFragment_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2")
                            .parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(new ArrayList<>(
                Arrays.asList(
                    PatientAttachmentLog.builder().filename("test_main.txt").mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(false)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_1.txt")
                        .mid("2")
                        .orderNum(0)
                        .parentMid("1")
                        .uploaded(true)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_2.txt")
                        .mid("3")
                        .orderNum(1)
                        .parentMid("1")
                        .uploaded(true)
                        .patientMigrationReqId(1)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .isBase64(true)
                        .skeleton(false).build()
                             )));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(patientAttachmentLogService, times(2))
            .deleteAttachmentLog(any(), eq(CONVERSATION_ID));
    }

    @Test
    public void When_RemoveAttachmentCalledForEachAttachmentLogFragment_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_frag_1.txt")
                            .mid("2")
                            .parentMid("1")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(new ArrayList<>(
                Arrays.asList(
                    PatientAttachmentLog.builder().filename("test_main.txt").mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(false)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_1.txt")
                        .mid("2")
                        .orderNum(0)
                        .parentMid("1")
                        .uploaded(true)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .patientMigrationReqId(1).build(),
                    PatientAttachmentLog.builder().filename("test_frag_2.txt")
                        .mid("3")
                        .orderNum(1)
                        .parentMid("1")
                        .uploaded(true)
                        .patientMigrationReqId(1)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .build()
                             )));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(attachmentHandlerService, times(2))
            .removeAttachment(any(), any());
    }

    @Test
    public void When_ParentCOPCMessageIncomingAfterFragments_Expect_RunWithNoErrors()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_main.txt")
                            .mid("1")
                            .parentMid("0")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(true, true, DATA_AMOUNT));


        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(attachmentHandlerService, times(1))
            .buildSingleFileStringFromPatientAttachmentLogs(any(), any());
    }

    @Test
    public void When_ParentCOPCMessageIncomingAfterFragments_Expect_CorrectPostProcessedLength()
        throws ValidationException, SAXException, AttachmentLogException,
               InlineAttachmentProcessingException, ExternalAttachmentProcessingException {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(PatientAttachmentLog.builder()
                            .filename("test_main.txt")
                            .mid("1")
                            .parentMid("0")
                            .patientMigrationReqId(1)
                            .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(createPatientAttachmentList(true, true, DATA_AMOUNT));

        when(attachmentHandlerService.buildSingleFileStringFromPatientAttachmentLogs(any(), any())).thenReturn(ATTACH);

        copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID);
        verify(attachmentHandlerService, times(1)).buildSingleFileStringFromPatientAttachmentLogs(any(), any());

        var argument = ArgumentCaptor.forClass(PatientAttachmentLog.class);
        verify(patientAttachmentLogService, times(1)).updateAttachmentLog(argument.capture(), eq(CONVERSATION_ID));
        assertEquals(ATTACH_LENGTH, argument.getValue().getPostProcessedLengthNum());
    }

    @Test
    public void When_MergedAttachmentsBase64LengthsMismatches_Expect_NotThrowsExternalAttachmentException() {

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessageFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        var inboundMessageId = xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId");

        when(patientAttachmentLogService.findAttachmentLog(inboundMessageId, CONVERSATION_ID))
            .thenReturn(
                PatientAttachmentLog.builder()
                    .filename("test_frag_1.txt")
                    .mid("2")
                    .parentMid("1")
                    .patientMigrationReqId(1)
                    .build());

        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(new ArrayList<>(
                Arrays.asList(
                    PatientAttachmentLog.builder().filename("test_main.txt")
                        .mid("1")
                        .orderNum(0)
                        .parentMid("0")
                        .uploaded(false)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(INVALID_ATTACH_LENGTH)
                        .skeleton(false)
                        .patientMigrationReqId(1)
                        .isBase64(true)
                        .build(),
                    PatientAttachmentLog.builder().filename("test_frag_1.txt")
                        .mid("2")
                        .orderNum(0)
                        .parentMid("1")
                        .uploaded(true)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .patientMigrationReqId(1)
                        .isBase64(true)
                        .build(),
                    PatientAttachmentLog.builder().filename("test_frag_2.txt")
                        .mid("3")
                        .orderNum(1)
                        .parentMid("1")
                        .uploaded(true)
                        .patientMigrationReqId(1)
                        .largeAttachment(true)
                        .originalBase64(true)
                        .compressed(false)
                        .contentType("text/plain")
                        .lengthNum(0)
                        .skeleton(false)
                        .isBase64(true)
                        .build()
                             )));

        when(attachmentHandlerService.buildSingleFileStringFromPatientAttachmentLogs(anyList(), anyString())).thenReturn(ATTACH);

        assertDoesNotThrow(() -> copcMessageHandler.checkAndMergeFileParts(inboundMessage, CONVERSATION_ID));
    }

    @Test
    public void When_HandleMessage_WithDbConnectionException_Expect_ExceptionThrown() throws SAXException {
        InboundMessage message = new InboundMessage();
        prepareFragmentMocks(message);
        prepareAttachmentLogs();

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(null)
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));

        doThrow(ConnectionException.class)
            .when(nackAckPreparationServiceMock).sendAckMessage(any(COPCIN000001UK01Message.class), eq(CONVERSATION_ID), anyString());

        assertThatThrownBy(() -> copcMessageHandler.handleMessage(message, CONVERSATION_ID))
            .isInstanceOf(ConnectionException.class);
    }

    @Test
    public void When_HandleMessage_WithWebClientRequestException_Expect_ExceptionThrown() throws SAXException {
        InboundMessage message = new InboundMessage();
        prepareFragmentMocks(message);
        prepareAttachmentLogs();

        when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
            .thenReturn(null)
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));

        doThrow(WebClientRequestException.class)
            .when(nackAckPreparationServiceMock).sendAckMessage(any(COPCIN000001UK01Message.class), eq(CONVERSATION_ID), anyString());

        assertThatThrownBy(() -> copcMessageHandler.handleMessage(message, CONVERSATION_ID))
            .isInstanceOf(WebClientRequestException.class);
    }

    @Test
    public void When_HandleMessage_WithMhsServerErrorException_Expect_ExceptionIsBubbledUpSoMessageIsRetriedByJMS()
        throws SAXException, InlineAttachmentProcessingException {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);

        try {
            InboundMessage message = new InboundMessage();
            prepareFragmentMocks(message);
            prepareAttachmentLogs();

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            when(patientAttachmentLogService.findAttachmentLog(MESSAGE_ID, CONVERSATION_ID))
                .thenReturn(null)
                .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));

            doThrow(MhsServerErrorException.class)
                .when(nackAckPreparationServiceMock).sendAckMessage(any(COPCIN000001UK01Message.class), eq(CONVERSATION_ID), anyString());

            assertThrows(
                MhsServerErrorException.class,
                () -> copcMessageHandler.handleMessage(message, CONVERSATION_ID));

        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    @Test
    @SneakyThrows
    public void When_HandleMessage_With_ProcessHasFailed_Expect_FailureHandled() {
        when(failedProcessHandlingService.hasProcessFailed(CONVERSATION_ID))
            .thenReturn(true);

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readCopcInboundMessageFromFile());

        copcMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(failedProcessHandlingService, times(1))
            .handleFailedProcess(any(COPCIN000001UK01Message.class), eq(CONVERSATION_ID));

        verify(migrationStatusLogService, times(0))
            .addMigrationStatusLog(COPC_MESSAGE_RECEIVED, CONVERSATION_ID, null, null);
    }

    @Test
    public void When_HandleMessage_With_SAXException_Expect_MigrationFailed() throws
        JsonProcessingException, AttachmentNotFoundException, JAXBException, BundleMappingException, InlineAttachmentProcessingException,
        SAXException, AttachmentLogException {
        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);
        InboundMessage message = new InboundMessage();

        try {

            prepareMocks();
            message.setPayload(readXmlFile("inbound_message_payload_fragment_index.xml"));

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            prepareFailProcessMocks(mockedXmlUnmarshall);

            doThrow(SAXException.class)
                .when(xPathService).parseDocumentFromXml(any());

            copcMessageHandler.handleMessage(message, CONVERSATION_ID);

            verify(nackAckPreparationServiceMock, times(1))
                .sendNackMessage(eq(LARGE_MESSAGE_GENERAL_FAILURE), any(COPCIN000001UK01Message.class), eq(CONVERSATION_ID));

            verify(sendNACKMessageHandler).prepareAndSendMessage(nackMessageDataCaptor.capture());
            assertThat(nackMessageDataCaptor.getValue().getNackCode()).isEqualTo(UNEXPECTED_CONDITION.getCode());

        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    @Test
    public void When_HandleMessage_With_ValidationException_Expect_MigrationFailed() throws SAXException, JsonProcessingException,
        JAXBException, InlineAttachmentProcessingException, AttachmentNotFoundException, BundleMappingException, AttachmentLogException {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);
        InboundMessage message = new InboundMessage();

        try {

            prepareFragmentMocks(message);

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            prepareFailProcessMocks(mockedXmlUnmarshall);

            doThrow(ValidationException.class)
                .when(attachmentHandlerService)
                .storeAttachmentWithoutProcessing(anyString(), anyString(), eq(CONVERSATION_ID), anyString());

            copcMessageHandler.handleMessage(message, CONVERSATION_ID);

            verify(nackAckPreparationServiceMock, times(1))
                .sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, mockCOPCMessage, CONVERSATION_ID);

            verify(sendNACKMessageHandler).prepareAndSendMessage(nackMessageDataCaptor.capture());
            assertThat(nackMessageDataCaptor.getValue().getNackCode()).isEqualTo(UNEXPECTED_CONDITION.getCode());

        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    @Test
    public void When_HandleMessage_With_ParseException_Expect_MigrationFailed() throws SAXException, JsonProcessingException,
        JAXBException, InlineAttachmentProcessingException, AttachmentNotFoundException, BundleMappingException, AttachmentLogException {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);
        MockedStatic<XmlParseUtilService> mockedXmlParseUtilService = Mockito.mockStatic(XmlParseUtilService.class);
        InboundMessage message = new InboundMessage();

        message.setExternalAttachments(List.of(
            new InboundMessage.ExternalAttachment("testId", "testId", "testTile", "testDescription"))
        );

        try {

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            mockedXmlParseUtilService.when(
                () -> XmlParseUtilService.parseContentType(anyString())
            ).thenThrow(ParseException.class);

            prepareFragmentMocks(message);
            prepareFailProcessMocks(mockedXmlUnmarshall);

            var messageId = "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1";
            when(patientAttachmentLogService.findAttachmentLog(messageId, CONVERSATION_ID))
                .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));

            when(xmlParseUtilService.getEbxmlAttachmentsData(any()))
                .thenReturn(buildCidReferenceDescription());

            copcMessageHandler.handleMessage(message, CONVERSATION_ID);

            verify(nackAckPreparationServiceMock, times(1))
                .sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, mockCOPCMessage, CONVERSATION_ID);

            verify(sendNACKMessageHandler).prepareAndSendMessage(nackMessageDataCaptor.capture());
            assertThat(nackMessageDataCaptor.getValue().getNackCode()).isEqualTo(UNEXPECTED_CONDITION.getCode());

        } finally {
            mockedXmlUnmarshall.close();
            mockedXmlParseUtilService.close();
        }
    }

    @Test
    public void When_HandleMessage_With_AttachmentProcessingErrorStorageCause_Expect_FailedWithUnexpectedCondition() throws
        JsonProcessingException, JAXBException, InlineAttachmentProcessingException, AttachmentNotFoundException, BundleMappingException,
        SAXException, AttachmentLogException {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);
        InboundMessage message = new InboundMessage();

        try {

            prepareFragmentMocks(message);

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            prepareFailProcessMocks(mockedXmlUnmarshall);

            doThrow(new InlineAttachmentProcessingException(
                "Test Inline Attachment Processing Exception", new StorageException("Test storage exception", new Exception()))
            ).when(attachmentHandlerService)
                .storeAttachmentWithoutProcessing(anyString(), anyString(), eq(CONVERSATION_ID), anyString());

            copcMessageHandler.handleMessage(message, CONVERSATION_ID);

            verify(nackAckPreparationServiceMock, times(1))
                .sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, mockCOPCMessage, CONVERSATION_ID);

            verify(sendNACKMessageHandler).prepareAndSendMessage(nackMessageDataCaptor.capture());
            assertThat(nackMessageDataCaptor.getValue().getNackCode()).isEqualTo(UNEXPECTED_CONDITION.getCode());

        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    @Test
    public void When_HandleMessage_With_AttachmentProcessingErrorNotStorageCause_Expect_FailedWithAttachmentsNotReceived() throws
        SAXException, JsonProcessingException, JAXBException, InlineAttachmentProcessingException, AttachmentNotFoundException,
        BundleMappingException, AttachmentLogException {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);
        InboundMessage message = new InboundMessage();

        try {

            prepareFragmentMocks(message);

            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(COPCIN000001UK01Message.class))
            ).thenReturn(mockCOPCMessage);

            prepareFailProcessMocks(mockedXmlUnmarshall);

            doThrow(InlineAttachmentProcessingException.class)
                .when(attachmentHandlerService)
                .storeAttachmentWithoutProcessing(anyString(), anyString(), eq(CONVERSATION_ID), anyString());


            copcMessageHandler.handleMessage(message, CONVERSATION_ID);

            verify(nackAckPreparationServiceMock, times(1))
                .sendNackMessage(LARGE_MESSAGE_GENERAL_FAILURE, mockCOPCMessage, CONVERSATION_ID);

            verify(sendNACKMessageHandler).prepareAndSendMessage(nackMessageDataCaptor.capture());
            assertEquals(LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode(), nackMessageDataCaptor.getValue().getNackCode());

            verify(migrationStatusLogService, times(1))
                .addMigrationStatusLog(LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getMigrationStatus(),
                                       CONVERSATION_ID,
                                       null,
                                       LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    private List<EbxmlReference> buildCidReferenceDescription() {
        EbxmlReference reference = new EbxmlReference("First instance is always a payload", "mid:xxxx-xxxx-xxxx-xxxx", "docId");
        EbxmlReference reference2 = new EbxmlReference("desc", "cid:28B31-4245-4AFC-8DA2-8A40623A5101", "docId");
        List<EbxmlReference> attachmentReferenceDescription = new ArrayList<>();
        attachmentReferenceDescription.add(reference);
        attachmentReferenceDescription.add(reference2);

        return attachmentReferenceDescription;
    }

    private PatientAttachmentLog buildPatientAttachmentLog(String mid, String parentMid, int orderNum,
                                                           boolean isUploaded, boolean isLargeAttachment) {
        return PatientAttachmentLog.builder()
            .mid(mid)
            .filename("E39E79A2-FA96-48FF-9373-7BBCB9D036E7.txt")
            .parentMid(parentMid)
            .patientMigrationReqId(1)
            .contentType("xml/text")
            .skeleton(false)
            .orderNum(orderNum)
            .uploaded(isUploaded)
            .largeAttachment(isLargeAttachment)
            .build();
    }
    private PatientAttachmentLog buildPatientAttachmentLog(String mid, String parentMid, boolean isLargeMessage) {
        return buildPatientAttachmentLog(mid, parentMid, 0, false, isLargeMessage);
    }

    private PatientAttachmentLog buildPartialPatientAttachmentLog(String mid, String contentType) {
        return PatientAttachmentLog.builder()
            .mid(mid)
            .filename("Filename=E39E79A2-FA96-48FF-9373-7BBCB9D036E7_1.messageattachment")
            .patientMigrationReqId(1)
            .contentType(contentType)
            .build();
    }

    @SneakyThrows
    private void prepareExpectedFragmentMocks(InboundMessage message) {

        setUpInboundMessageAndMocks(message);
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(Arrays.asList(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB",
            "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 0, true, true), buildPatientAttachmentLog("057C22B4-613F-47D3-9A72-44A1758464FB",
                    "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 1, false, true)));
    }

    private void setUpInboundMessageAndMocks(InboundMessage message) throws SAXException {
        prepareMocks();
        message.setPayload(readXmlFile("inbound_message_payload_fragment_index.xml"));
        String ebxml = readXmlFile("inbound_message_ebxml_fragment_index.xml");
        message.setEbXML(ebxml);
        message.setAttachments(Arrays.asList(new InboundMessage.Attachment("xml/text", "Yes", "Filename=E39E79A2-FA96-48FF-9373"
            + "-7BBCB9D036E7_1.messageattachment ContentType=text/plain Compressed=No LargeAttachment=No OriginalBase64=Yes", "this is a "
            + "payload")));
        message.getAttachments().getFirst().setPayload("This is a payload");

        when(xPathService.parseDocumentFromXml(message.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
            .thenReturn("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1");
    }

    @SneakyThrows
    private void prepareFragmentIndexWithCidMocks(InboundMessage message) {

        prepareMocks();
        message.setPayload(readXmlFile("inbound_message_payload_fragment_index.xml"));
        String ebxml = readXmlFile("inbound_message_ebxml_fragment_index.xml");
        message.setEbXML(ebxml);

        InboundMessage.Attachment attachment = new InboundMessage.Attachment("text/plain", "Yes",
            "Filename=\"CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1_0.messageattachment\" ContentType=application/x-gzip"
                + "Compressed=No LargeAttachment=No OriginalBase64=Yes", "ABC Not Required");
        InboundMessage.ExternalAttachment extAttachment = new InboundMessage.ExternalAttachment("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1",
            "28B31-4245-4AFC-8DA2-8A40623A5101", "E39E79A2-FA96-48FF-9373-7BBCB9D036E7_0.messageattachment", "Filename=E39E79A2-FA96-48FF"
            + "-9373-7BBCB9D036E7_0.messageattachment ContentType=text/plain Compressed=No LargeAttachment=No OriginalBase64=Yes");

        message.setAttachments(Arrays.asList(attachment));
        message.setExternalAttachments(Arrays.asList(extAttachment));

        when(xPathService.parseDocumentFromXml(message.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
            .thenReturn("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1");
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(Arrays.asList(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB",
                "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 0, true, true), buildPatientAttachmentLog("057C22B4-613F-47D3-9A72-44A1758464FB",
                "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 1, false, true)));
    }

    @SneakyThrows
    private void prepareFragmentIndexMocks(InboundMessage message) {

        prepareMocks();

        message.setPayload(readXmlFile("inbound_message_payload_fragment_index.xml"));
        String ebxml = readXmlFile("inbound_message_ebxml_fragment_index.xml");
        message.setEbXML(ebxml);

        InboundMessage.ExternalAttachment extAttachment = new InboundMessage.ExternalAttachment("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1",
            "28B31-4245-4AFC-8DA2-8A40623A5101", "E39E79A2-FA96-48FF-9373-7BBCB9D036E7_0.messageattachment", "Filename=\"E39E79A2-FA96-48FF"
            + "-9373-7BBCB9D036E7_0.messageattachment\" ContentType=text/plain Compressed=No LargeAttachment=No OriginalBase64=Yes");
        InboundMessage.ExternalAttachment extAttachment1 = new InboundMessage.ExternalAttachment("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1",
            "28B31-4245-4AFC-8DA2-8A40623A5101", "E39E79A2-FA96-48FF-9373-7BBCB9D036E7_1.messageattachment", "\"Filename=E39E79A2-FA96-48FF"
            + "-9373-7BBCB9D036E7_0.messageattachment\" ContentType=text/plain Compressed=No LargeAttachment=No OriginalBase64=Yes");

        message.setExternalAttachments(Arrays.asList(extAttachment, extAttachment1));

        when(xPathService.parseDocumentFromXml(message.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
            .thenReturn("CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1");
        when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(Arrays.asList(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB",
            "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 0, true, true), buildPatientAttachmentLog("057C22B4-613F-47D3-9A72-44A1758464FB",
            "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 1, false, true)));
    }

    private void prepareFragmentMocks(InboundMessage inboundMessage) throws SAXException {

        prepareMocks();

        inboundMessage.setPayload(readXmlFile("inbound_message_payload_fragment_index.xml"));
        inboundMessage.setEbXML(readXmlFile("inbound_message_ebxml_fragment_index.xml"));
        inboundMessage.setAttachments(Arrays.asList(new InboundMessage.Attachment("xml/text", "Yes", "Filename=\"E39E79A2-FA96-48FF-9373"
            + "-7BBCB9D036E7_1.messageattachment\" ContentType=text/plain Compressed=No LargeAttachment=No OriginalBase64=Yes", "")));
        inboundMessage.getAttachments().getFirst().setPayload("This is a payload");

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId")).thenReturn("CBBAE92D-C7E8"
            + "-4A9C-8887-F5AEBA1F8CE1").thenReturn("047C22B4-613F-47D3-9A72-44A1758464FB");

        var attachmentLog1 = buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB",
            "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 0, true, true);

        var attachmentLog2 = buildPatientAttachmentLog("057C22B4-613F-47D3-9A72-44A1758464FB",
            "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", 1, false, true);

        var attachmentArray = Arrays.asList(attachmentLog1, attachmentLog2);
        lenient().when(patientAttachmentLogService.findAttachmentLogs(CONVERSATION_ID))
            .thenReturn(attachmentArray);

        when(idGeneratorService.generateUuid()).thenReturn(TEST_ID);
    }

    private void prepareMocks() {

        PatientMigrationRequest migrationRequest =
            PatientMigrationRequest.builder()
                .id(1)
                .losingPracticeOdsCode(LOSING_ODE_CODE)
                .winningPracticeOdsCode(WINNING_ODE_CODE)
                .build();

        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);
    }

    private void prepareAttachmentLogs() {
        var messageId = "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1";
        when(patientAttachmentLogService.findAttachmentLog(messageId, CONVERSATION_ID)).thenReturn(null)
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", null, true));
        when(patientAttachmentLogService.findAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", CONVERSATION_ID))
            .thenReturn(buildPatientAttachmentLog("047C22B4-613F-47D3-9A72-44A1758464FB", "CBBAE92D-C7E8-4A9C-8887-F5AEBA1F8CE1", true));
    }

    @SneakyThrows
    private String readXmlFile(String file) {
        return readResourceAsString("/xml/COPCMessageHandler/" + file);
    }

    @SneakyThrows
    private String readCopcInboundMessageFromFile() {
        return readResourceAsString("/xml/COPC_IN000001UK01_subsequent_message/payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readInboundMessageFromFile() {
        return readResourceAsString("/xml/inbound_message_payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readLargeInboundMessageEbXmlFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06_LARGE_MSG/ebxml.xml");
    }

    private ArrayList<PatientAttachmentLog> createPatientAttachmentList(Boolean isParentUploaded,
        Boolean isFragmentUploaded, Integer amount) {
        var patientAttachmentLogs = new ArrayList<PatientAttachmentLog>(amount);
        patientAttachmentLogs.add(
            PatientAttachmentLog.builder().filename("test_main.txt").mid("1")
                .orderNum(0)
                .parentMid("0")
                .uploaded(!!isParentUploaded)
                .largeAttachment(true)
                .originalBase64(true)
                .compressed(false)
                .contentType("text/plain")
                .lengthNum(0)
                .skeleton(false)
                .isBase64(true)
                .patientMigrationReqId(1).build()
        );
        for (var i = 1; i <= amount; i++) {
            patientAttachmentLogs.add(
                PatientAttachmentLog.builder()
                    .filename("text_frag_" + i + ".txt")
                    .mid(Integer.toString(i + 1))
                    .orderNum(0)
                    .parentMid("1")
                    .uploaded(!!isFragmentUploaded)
                    .largeAttachment(true)
                    .originalBase64(true)
                    .compressed(false)
                    .contentType("text/plain")
                    .lengthNum(0)
                    .skeleton(false)
                    .isBase64(true)
                    .patientMigrationReqId(1).build()
            );
        }
        return patientAttachmentLogs;
    }

    private void prepareFailProcessMocks(MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall) throws JsonProcessingException {

        mockedXmlUnmarshall.when(
            () -> XmlUnmarshallUtil.unmarshallString(anyString(), eq(RCMRIN030000UKMessage.class))
        ).thenReturn(mockEhrExtract);

        when(outboundMessageUtil.parseFromAsid(any())).thenReturn(StringUtils.EMPTY);
        when(outboundMessageUtil.parseMessageRef(any())).thenReturn(StringUtils.EMPTY);
        when(outboundMessageUtil.parseToAsid(any())).thenReturn(StringUtils.EMPTY);
        when(outboundMessageUtil.parseToOdsCode(any())).thenReturn(StringUtils.EMPTY);

        when(inboundMessageUtil.readMessage(any())).thenReturn(mockInboundMessage);
        when(mockInboundMessage.getPayload()).thenReturn(StringUtils.EMPTY);
    }
}