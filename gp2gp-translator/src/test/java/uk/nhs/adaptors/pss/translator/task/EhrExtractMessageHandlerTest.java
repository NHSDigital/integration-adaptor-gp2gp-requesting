package uk.nhs.adaptors.pss.translator.task;

import static java.util.UUID.randomUUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_RECEIVED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_TRANSLATED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.ValidationException;
import javax.xml.transform.TransformerException;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.jdbi.v3.core.ConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.parser.DataFormatException;
import lombok.SneakyThrows;
import uk.nhs.adaptors.common.util.fhir.FhirParser;
import uk.nhs.adaptors.connector.dao.PatientMigrationRequestDao;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.pss.translator.exception.AttachmentNotFoundException;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.exception.InlineAttachmentProcessingException;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.AttachmentHandlerService;
import uk.nhs.adaptors.pss.translator.service.AttachmentReferenceUpdaterService;
import uk.nhs.adaptors.pss.translator.service.BundleMapperService;
import uk.nhs.adaptors.pss.translator.service.FailedProcessHandlingService;
import uk.nhs.adaptors.pss.translator.service.NackAckPrepInterface;
import uk.nhs.adaptors.pss.translator.service.SkeletonProcessingService;
import uk.nhs.adaptors.pss.translator.service.XPathService;
import uk.nhs.adaptors.pss.translator.storage.StorageException;

@ExtendWith(MockitoExtension.class)
public class EhrExtractMessageHandlerTest {
    private static final String NHS_NUMBER = "123456";
    private static final String CONVERSATION_ID = randomUUID().toString();
    private static final String INBOUND_MESSAGE_STRING = "{hi i'm inbound message}";
    private static final String BUNDLE_STRING = "{bundle}";
    private static final String LOSING_ODE_CODE = "G543";
    private static final String WINNING_ODE_CODE = "B943";
    private static final String MESSAGE_ID = randomUUID().toString();
    private static final String MESSAGE_ID_PATH = "/Envelope/Header/MessageHeader/MessageData/MessageId";
    public static final int MIGRATION_REQUEST_ID = 999;


    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MigrationStatusLogService migrationStatusLogService;

    @Mock
    private PatientMigrationRequestDao migrationRequestDao;

    @Mock
    private AttachmentHandlerService attachmentHandlerService;

    @Mock
    private AttachmentReferenceUpdaterService attachmentReferenceUpdaterService;

    @Mock
    private FhirParser fhirParser;

    @Mock
    private BundleMapperService bundleMapperService;

    @Mock
    private PatientAttachmentLogService patientAttachmentLogService;

    @Mock
    private SendContinueRequestHandler sendContinueRequestHandler;

    @Mock
    private XPathService xPathService;

    @Mock
    private Document ebXmlDocument;

    @InjectMocks
    private EhrExtractMessageHandler ehrExtractMessageHandler;

    @Mock
    private NackAckPrepInterface nackAckPreparationServiceMock;

    @Mock
    private SkeletonProcessingService skeletonProcessingService;

    @Mock
    private PatientAttachmentLog patientAttachmentLog;
    @Mock
    private FailedProcessHandlingService failedProcessHandlingService;

    @Captor
    private ArgumentCaptor<PatientAttachmentLog> patientAttachmentLogCaptor;

    @Test
    public void  When_HandleMessageWithValidDataIsCalled_Expect_CallsMigrationStatusLogServiceAddMigrationStatusLog()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        prepareMocks(inboundMessage);

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
                .thenReturn(MESSAGE_ID);

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(migrationStatusLogService).addMigrationStatusLog(EHR_EXTRACT_RECEIVED, CONVERSATION_ID, null, null);

        verify(migrationStatusLogService).updatePatientMigrationRequestAndAddMigrationStatusLog(
            CONVERSATION_ID,
            BUNDLE_STRING,
            INBOUND_MESSAGE_STRING,
            EHR_EXTRACT_TRANSLATED,
            MESSAGE_ID
        );
    }

    @Test
    public void When_HandleMessageWithValidDataIsCalled_Expect_CallsBundleMapperServiceMapToBundle()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        prepareMocks(inboundMessage);

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
                .thenReturn("6E242658-3D8E-11E3-A7DC-172BDA00FA67");

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        // mapped item is private to the class, so we cannot test an exact object
        verify(bundleMapperService).mapToBundle(any(RCMRIN030000UKMessage.class), any(), any());
    }

    @Test
    public void When_HandleMessageWithValidDataIsCalled_Expect_CallsAttachmentHandlerServiceStoreAttachments()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        prepareMocks(inboundMessage);

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
                .thenReturn("6E242658-3D8E-11E3-A7DC-172BDA00FA67");

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(attachmentHandlerService).storeAttachments(inboundMessage.getAttachments(), CONVERSATION_ID);
    }

    @Test
    public void When_HandleMessageWithValidDataIsCalled_Expect_CallsAttachmentReferenceUpdaterServiceUpdateReferences()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        prepareMocks(inboundMessage);

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
                .thenReturn("6E242658-3D8E-11E3-A7DC-172BDA00FA67");

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(attachmentReferenceUpdaterService).replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload());
    }

    @Test
    public void When_HandleLargeMessageWithValidDataIsCalled_Expect_CallSendContinueRequest()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
                new InboundMessage.ExternalAttachment(
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                        "66B41202-C358-4B4C-93C6-7A10803F9584",
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                        "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                                + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                                + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();
        when(xPathService.getNodeValue(any(), eq(MESSAGE_ID_PATH))).thenReturn(MESSAGE_ID);

        EhrExtractMessageHandler ehrExtractMessageHandlerSpy = Mockito.spy(ehrExtractMessageHandler);
        ehrExtractMessageHandlerSpy.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(ehrExtractMessageHandlerSpy).sendContinueRequest(
            any(RCMRIN030000UKMessage.class),
            anyString(),
            anyString(),
            anyString(),
            any(Instant.class),
            anyString()
        );
    }


    @Test
    public void When_HandleMessageWithValidDataIsCalled_Expect_CallsStatusLogServiceUpdatePatientMigrationRequestAndAddMigrationStatusLog()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        prepareMocks(inboundMessage);

        when(xPathService.parseDocumentFromXml(inboundMessage.getEbXML())).thenReturn(ebXmlDocument);
        when(xPathService.getNodeValue(ebXmlDocument, "/Envelope/Header/MessageHeader/MessageData/MessageId"))
                .thenReturn(MESSAGE_ID);

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(migrationStatusLogService).updatePatientMigrationRequestAndAddMigrationStatusLog(
            CONVERSATION_ID, BUNDLE_STRING, INBOUND_MESSAGE_STRING, EHR_EXTRACT_TRANSLATED, MESSAGE_ID);
    }

    @Test
    public void When_HandleMessage_WithStoreAttachmentsThrows_Expect_InlineAttachmentProcessingException()
        throws JAXBException,
        InlineAttachmentProcessingException {
        InboundMessage inboundMessage = new InboundMessage();
        Bundle bundle = new Bundle();
        bundle.setId("Test");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setAttachments(new ArrayList<>());

        PatientMigrationRequest migrationRequest =
            PatientMigrationRequest.builder()
                .losingPracticeOdsCode(LOSING_ODE_CODE)
                .winningPracticeOdsCode(WINNING_ODE_CODE)
                .build();

        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);

        doThrow(new InlineAttachmentProcessingException("Test Exception"))
            .when(attachmentHandlerService).storeAttachments(any(), any());

        assertThrows(InlineAttachmentProcessingException.class, () ->
                        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID));
    }

    @Test
    public void When_HandleMessage_WithStorageExceptionCause_Expect_UnexpectedConditionNack() throws JAXBException,
        InlineAttachmentProcessingException {
        InboundMessage inboundMessage = new InboundMessage();
        Bundle bundle = new Bundle();
        bundle.setId("Test");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setAttachments(new ArrayList<>());

        PatientMigrationRequest migrationRequest =
            PatientMigrationRequest.builder()
                .losingPracticeOdsCode(LOSING_ODE_CODE)
                .winningPracticeOdsCode(WINNING_ODE_CODE)
                .build();

        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);

        doThrow(new InlineAttachmentProcessingException("Test Exception",
            new StorageException("Cause Test Exception", new RuntimeException())))
            .when(attachmentHandlerService).storeAttachments(any(), any());

        assertThrows(InlineAttachmentProcessingException.class, () ->
            ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID));

        verify(nackAckPreparationServiceMock, times(1))
            .sendNackMessage(eq(UNEXPECTED_CONDITION), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID));
    }

    @Test
    public void When_HandleMessage_WithMapToBundleThrows_Expect_BundleMappingException()
            throws BundleMappingException, AttachmentNotFoundException, ValidationException, InlineAttachmentProcessingException {
        InboundMessage inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setExternalAttachments(new ArrayList<>());


        PatientMigrationRequest migrationRequest =
            PatientMigrationRequest.builder()
                .losingPracticeOdsCode(LOSING_ODE_CODE)
                .winningPracticeOdsCode(WINNING_ODE_CODE)
                .build();

        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);
        when(attachmentReferenceUpdaterService.replaceOriginalFilenameWithStorageFilenameInEhrExtract(
            inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
        )).thenReturn(inboundMessage.getPayload());

        doThrow(new BundleMappingException("Test Exception"))
            .when(bundleMapperService).mapToBundle(any(RCMRIN030000UKMessage.class), any(), any());

        assertThrows(BundleMappingException.class,
                     () -> ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID));
    }

    @Test
    public void When_HandleMessage_WithEncodeToJsonThrows_Expect_DataFormatException()
            throws BundleMappingException, AttachmentNotFoundException, ValidationException, InlineAttachmentProcessingException {
        InboundMessage inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        Bundle bundle = new Bundle();
        bundle.setId("Test");
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setExternalAttachments(new ArrayList<>());

        PatientMigrationRequest migrationRequest =
            PatientMigrationRequest.builder()
                .losingPracticeOdsCode(LOSING_ODE_CODE)
                .winningPracticeOdsCode(WINNING_ODE_CODE)
                .build();

        when(bundleMapperService.mapToBundle(any(RCMRIN030000UKMessage.class), eq(LOSING_ODE_CODE), any())).thenReturn(bundle);
        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);
        when(attachmentReferenceUpdaterService.replaceOriginalFilenameWithStorageFilenameInEhrExtract(
            inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
        )).thenReturn(inboundMessage.getPayload());

        doThrow(new DataFormatException()).when(fhirParser).encodeToJson(bundle);

        assertThrows(DataFormatException.class,
                     () -> ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID));
    }

    @Test
    public void When_HandleSingleMessageWithValidDataIsCalled_Expect_NotToCallSendContinueRequest()
        throws JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();

        inboundMessage.setPayload(readInboundSingleMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundSingleMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        when(attachmentReferenceUpdaterService
                .replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                        inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
                )).thenReturn(inboundMessage.getPayload());

        EhrExtractMessageHandler ehrExtractMessageHandlerSpy = Mockito.spy(ehrExtractMessageHandler);
        ehrExtractMessageHandlerSpy.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(ehrExtractMessageHandlerSpy, times(0)).sendContinueRequest(
                any(RCMRIN030000UKMessage.class),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                anyString()
        );
    }

    @Test
    public void When_HandleSingleMessageWithSkeletonAttachmentAndValidDataIsCalled_Expect_CallToSkeletonProcessingService()
        throws JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        var attachment = new InboundMessage.Attachment("text/xml",
            "true", "ContentType=text/xml isCompressed=No isLargeAttachment=No "
            + "OriginalBase64=Yes Filename=\"test.txt\" DomainData=\"X-GP2GP-Skeleton: Yes\" ", "abcdefghi");

        var attachmentList = new ArrayList<InboundMessage.Attachment>();
        attachmentList.add(attachment);
        inboundMessage.setAttachments(attachmentList);
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();

        inboundMessage.setPayload(readInboundSingleMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundSingleMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        when(xPathService.getNodeValue(any(), any())).thenReturn("MESSAGE-ID");
        when(skeletonProcessingService.updateInboundMessageWithSkeleton(any(), any(), any())).thenReturn(inboundMessage);
        when(attachmentReferenceUpdaterService
            .replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
            )).thenReturn(inboundMessage.getPayload());

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        verify(skeletonProcessingService, times(1)).updateInboundMessageWithSkeleton(any(), any(), any());
    }

    @Test
    public void When_HandleSingleMessageWithAFilenameDescription_Expect_PatientAttachmentLogEntryGenerated()
            throws JsonProcessingException,
            JAXBException,
            InlineAttachmentProcessingException,
            BundleMappingException,
            AttachmentNotFoundException,
            ParseException,
            SAXException, TransformerException {

        InboundMessage inboundMessage = new InboundMessage();
        var attachment = new InboundMessage.Attachment("text/xml", "true", "test.txt", "abcdefghi");

        var attachmentList = new ArrayList<InboundMessage.Attachment>();
        attachmentList.add(attachment);
        inboundMessage.setAttachments(attachmentList);

        inboundMessage.setPayload(readInboundSingleMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundSingleMessageEbXmlFromFile());

        prepareMigrationRequestAndMigrationStatusMocks();

        when(xPathService.getNodeValue(any(), any())).thenReturn("MESSAGE-ID");
        when(attachmentReferenceUpdaterService
                .replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                        inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
                )).thenReturn(inboundMessage.getPayload());

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(patientAttachmentLogService, times(1)).addAttachmentLog(patientAttachmentLogCaptor.capture());
        PatientAttachmentLog log = patientAttachmentLogCaptor.getValue();
        assertEquals("MESSAGE-ID", log.getMid());
        assertEquals("test.txt", log.getFilename());
        assertNull(log.getParentMid());
        assertEquals(MIGRATION_REQUEST_ID, log.getPatientMigrationReqId());
        assertEquals(attachment.getContentType(), log.getContentType());
        assertFalse(log.getCompressed());
        assertFalse(log.getLargeAttachment());
        assertFalse(log.getOriginalBase64());
        assertFalse(log.getSkeleton());
        assertTrue(log.getUploaded());
        assertEquals(0, log.getLengthNum());
        assertEquals(attachment.getPayload().length(), log.getPostProcessedLengthNum());
        assertEquals(0, log.getOrderNum());
    }

    @Test
    public void When_HandleLargeMessageWithValidDataIsCalled_Expect_ItShouldNotTranslate()
        throws JAXBException, BundleMappingException, AttachmentNotFoundException,
               ParseException, JsonProcessingException, InlineAttachmentProcessingException,
               SAXException, TransformerException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
            new InboundMessage.ExternalAttachment(
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                "66B41202-C358-4B4C-93C6-7A10803F9584",
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                    + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                    + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );
        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        verify(bundleMapperService, times(0)).mapToBundle(any(), any(), any());
    }

    @Test
    public void When_HandleLargeMessageWithValidDataIsCalled_Expect_AddAttachmentExactNumberOfTimesAsExternalAttachmentsList()
        throws
        JsonProcessingException,
        JAXBException,
        InlineAttachmentProcessingException,
        BundleMappingException,
        AttachmentNotFoundException,
        ParseException,
        SAXException, TransformerException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();

        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
                new InboundMessage.ExternalAttachment(
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                        "66B41202-C358-4B4C-93C6-7A10803F9584",
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                        "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                                + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                                + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );
        externalAttachmentsTestList.add(
                new InboundMessage.ExternalAttachment(
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                        "66B41202-C358-4B4C-93C6-7A10803F9584",
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                        "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                                + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                                + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );
        externalAttachmentsTestList.add(
                new InboundMessage.ExternalAttachment(
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                        "66B41202-C358-4B4C-93C6-7A10803F9584",
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                        "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                                + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes")
        );
        externalAttachmentsTestList.add(
                new InboundMessage.ExternalAttachment(
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                        "66B41202-C358-4B4C-93C6-7A10803F9584",
                        "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                        "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                                + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        verify(patientAttachmentLogService, times(externalAttachmentsTestList.size())).addAttachmentLog(any());
    }

    @Test
    public void When_HandleMessage_WithWebClientRequestException_Expect_ExceptionThrown() {
        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
            new InboundMessage.ExternalAttachment(
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                "66B41202-C358-4B4C-93C6-7A10803F9584",
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                    + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                    + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        doThrow(WebClientRequestException.class)
            .when(sendContinueRequestHandler).prepareAndSendRequest(any());

        assertThatThrownBy(() -> ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID))
            .isInstanceOf(WebClientRequestException.class);
    }
    @Test
    public void When_HandleMessage_WithConnectionException_Expect_ExceptionThrown() {
        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
            new InboundMessage.ExternalAttachment(
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                "66B41202-C358-4B4C-93C6-7A10803F9584",
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                    + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                    + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        doThrow(ConnectionException.class)
            .when(sendContinueRequestHandler).prepareAndSendRequest(any());

        assertThatThrownBy(() -> ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID))
            .isInstanceOf(ConnectionException.class);
    }

    @Test
    public void When_HandleMessage_WithMhsServerErrorException_Expect_MigrationFailed() {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
            new InboundMessage.ExternalAttachment(
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                "66B41202-C358-4B4C-93C6-7A10803F9584",
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                    + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                    + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        doThrow(MhsServerErrorException.class)
            .when(sendContinueRequestHandler).prepareAndSendRequest(any());

        assertThatThrownBy(() -> ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID))
            .isInstanceOf(MhsServerErrorException.class);

        verify(nackAckPreparationServiceMock).sendNackMessage(eq(UNEXPECTED_CONDITION), any(RCMRIN030000UKMessage.class), anyString());
    }

    @Test
    public void When_HandleMessage_WithDuplicateMessage_Expect_AttachmentIsNotLoggedMoreThanOnce() throws AttachmentNotFoundException,
        JAXBException, BundleMappingException, ParseException, JsonProcessingException, TransformerException,
        InlineAttachmentProcessingException, SAXException {

        Bundle bundle = new Bundle();
        bundle.setId("Test");

        InboundMessage inboundMessage = new InboundMessage();
        List<InboundMessage.ExternalAttachment> externalAttachmentsTestList = new ArrayList<>();
        externalAttachmentsTestList.add(
            new InboundMessage.ExternalAttachment(
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs",
                "66B41202-C358-4B4C-93C6-7A10803F9584",
                "68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1",
                "Filename=\"68E2A39F-7A24-449D-83CC-1B7CF1A9DAD7spine.nhs.ukExample1.gzip\" "
                    + "ContentType=text/xml Compressed=Yes LargeAttachment=No OriginalBase64=Yes "
                    + "DomainData=\"X-GP2GP-Skeleton: Yes\"")
        );

        inboundMessage.setPayload(readLargeInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readLargeInboundMessageEbXmlFromFile());
        inboundMessage.setExternalAttachments(externalAttachmentsTestList);

        prepareMigrationRequestAndMigrationStatusMocks();

        when(patientAttachmentLogService.findAttachmentLog(externalAttachmentsTestList.getFirst().getMessageId(), CONVERSATION_ID))
            .thenReturn(null)
                .thenReturn(patientAttachmentLog);

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);
        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(patientAttachmentLogService, times(1)).addAttachmentLog(any());
    }

    @Test
    @SneakyThrows
    public void When_HandleMessage_With_ProcessHasFailed_Expect_FailureHandled()
        throws AttachmentNotFoundException, JAXBException, BundleMappingException, ParseException,
               JsonProcessingException, TransformerException, SAXException {

        when(failedProcessHandlingService.hasProcessFailed(CONVERSATION_ID))
            .thenReturn(true);

        InboundMessage inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readInboundMessagePayloadFromFile());

        ehrExtractMessageHandler.handleMessage(inboundMessage, CONVERSATION_ID);

        verify(failedProcessHandlingService, times(1))
            .handleFailedProcess(any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID));

        verify(migrationStatusLogService, times(0))
            .addMigrationStatusLog(EHR_EXTRACT_RECEIVED, CONVERSATION_ID, null, null);
    }

    @SneakyThrows
    private void prepareMocks(InboundMessage inboundMessage) throws BundleMappingException {
        inboundMessage.setPayload("payload");
        Bundle bundle = new Bundle();
        bundle.setId("Test");

        inboundMessage.setPayload(readInboundMessagePayloadFromFile());
        inboundMessage.setEbXML(readInboundMessageEbXmlFromFile());
        inboundMessage.setAttachments(new ArrayList<>());
        inboundMessage.setExternalAttachments(new ArrayList<>());

        prepareMigrationRequestAndMigrationStatusMocks();

        // imported from main on merge
        when(fhirParser.encodeToJson(bundle)).thenReturn(BUNDLE_STRING);
        when(objectMapper.writeValueAsString(inboundMessage)).thenReturn(INBOUND_MESSAGE_STRING);
        when(bundleMapperService.mapToBundle(any(RCMRIN030000UKMessage.class), eq(LOSING_ODE_CODE), any())).thenReturn(bundle);
        when(attachmentReferenceUpdaterService
                .replaceOriginalFilenameWithStorageFilenameInEhrExtract(
                        inboundMessage.getAttachments(), CONVERSATION_ID, inboundMessage.getPayload()
                )).thenReturn(inboundMessage.getPayload());
    }

    @SneakyThrows
    private void prepareMigrationRequestAndMigrationStatusMocks() {

        PatientMigrationRequest migrationRequest =
                PatientMigrationRequest.builder()
                        .losingPracticeOdsCode(LOSING_ODE_CODE)
                        .winningPracticeOdsCode(WINNING_ODE_CODE)
                        .id(MIGRATION_REQUEST_ID)
                        .build();

        MigrationStatusLog migrationStatusLog =
                MigrationStatusLog.builder()
                        .date(OffsetDateTime.ofInstant(
                                Instant.now(),
                                ZoneId.systemDefault()))
                        .build();

        when(migrationRequestDao.getMigrationRequest(CONVERSATION_ID)).thenReturn(migrationRequest);
        when(migrationStatusLogService.getLatestMigrationStatusLog(CONVERSATION_ID)).thenReturn(migrationStatusLog);
    }

    @SneakyThrows
    private String readInboundMessagePayloadFromFile() {
        return readResourceAsString("/xml/inbound_message_payload.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readInboundMessageEbXmlFromFile() {
        return readResourceAsString("/xml/inbound_message_ebxml.xml");
    }

    private String readInboundSingleMessageEbXmlFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06/ebxmlSmallMessage.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    private String readInboundSingleMessagePayloadFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06/payloadSmallMessage.xml").replace("{{nhsNumber}}", NHS_NUMBER);
    }

    @SneakyThrows
    private String readLargeInboundMessagePayloadFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06_LARGE_MSG/payload.xml");
    }

    @SneakyThrows
    private String readLargeInboundMessageEbXmlFromFile() {
        return readResourceAsString("/xml/RCMRIN030000UK06_LARGE_MSG/ebxml.xml");
    }
}
