package uk.nhs.adaptors.pss.translator.task.scheduled;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_TIMEOUT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.hl7.v3.RCMRIN030000UKMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.connector.model.MigrationStatusLog;
import uk.nhs.adaptors.connector.model.PatientMigrationRequest;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.connector.service.PatientMigrationRequestService;
import uk.nhs.adaptors.pss.translator.config.TimeoutProperties;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.exception.SdsRetrievalException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.service.PersistDurationService;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;
import uk.nhs.adaptors.pss.translator.util.InboundMessageUtil;
import uk.nhs.adaptors.pss.translator.util.OutboundMessageUtil;
import uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EHRTimeoutHandlerTest {

    private static final String EHR_EXTRACT_MESSAGE_NAME = "RCMR_IN030000UK06";
    private static final String COPC_MESSAGE_NAME = "COPC_IN000001UK01";

    private static final int EHR_EXTRACT_PERSIST_DURATION = 7;
    private static final int COPC_PERSIST_DURATION = 4;
    private static final int ATTACHMENT_COUNT_THREE = 3;
    private static final ZonedDateTime TEN_DAYS_AGO = ZonedDateTime.of(LocalDateTime.now().minusDays(10), ZoneId.systemDefault());
    private static final ZonedDateTime TEN_DAYS_TIME = ZonedDateTime.of(LocalDateTime.now().plusDays(10), ZoneId.systemDefault());
    private static final long TEN_DAYS = 10;
    private static final int SIXTY_SECONDS = 60;
    private static final int ONE_SECOND = 1;
    private static final String INBOUND_MESSAGE_STRING = "test inbound Message";
    private static final String INBOUND_MESSAGE_STRING_TWO = "test inbound Message 2";
    private static final String EBXML_STRING = "test ebXML";
    private static final String EBXML_STRING_TWO = "test ebXML 2";
    private static final String UNEXPECTED_CONDITION_CODE = "99";

    @Captor
    private ArgumentCaptor<NACKMessageData> nackMessageData;
    @Mock
    private PersistDurationService persistDurationService;
    @Mock
    private PatientMigrationRequestService migrationRequestService;
    @Mock
    private MDCService mdcService;
    @Mock
    private TimeoutProperties timeoutProperties;
    @Mock
    private SendNACKMessageHandler sendNACKMessageHandler;
    @Mock
    private OutboundMessageUtil outboundMessageUtil;
    @Mock
    private InboundMessageUtil inboundMessageUtil;
    @Mock
    private MigrationStatusLogService migrationStatusLogService;
    @Mock
    private PatientAttachmentLogService patientAttachmentLogService;
    @Mock(name = "mockInboundMessage")
    private InboundMessage mockInboundMessage;
    @Mock(name = "mockInboundMessage2")
    private InboundMessage mockInboundMessage2;
    @Mock
    private RCMRIN030000UKMessage mockedPayload;
    @Mock(name = "mockRequest")
    private PatientMigrationRequest mockRequest;
    @Mock(name = "mockRequest2")
    private PatientMigrationRequest mockRequest2;
    @Mock
    private DateUtils dateUtils;
    @InjectMocks
    private EHRTimeoutHandler ehrTimeoutHandler;

    private static Stream<Arguments> inProgressRequestsWithAttachments() {
        return Stream.of(
            Arguments.of(EHR_EXTRACT_PROCESSING),
            Arguments.of(CONTINUE_REQUEST_ACCEPTED),
            Arguments.of(COPC_MESSAGE_RECEIVED),
            Arguments.of(COPC_MESSAGE_PROCESSING),
            Arguments.of(COPC_ACKNOWLEDGED)
            );
    }

    private static Stream<Arguments> preEhrParsedRequests() {
        return Stream.of(
            Arguments.of(REQUEST_RECEIVED),
            Arguments.of(EHR_EXTRACT_REQUEST_ACCEPTED),
            Arguments.of(EHR_EXTRACT_REQUEST_ACKNOWLEDGED),
            Arguments.of(EHR_EXTRACT_RECEIVED)
        );
    }

    private void setupMocks() throws JsonProcessingException {
        when(persistDurationService.getPersistDurationFor(any(), eq(EHR_EXTRACT_MESSAGE_NAME)))
            .thenReturn(Duration.ofHours(EHR_EXTRACT_PERSIST_DURATION));
        when(persistDurationService.getPersistDurationFor(any(), eq(COPC_MESSAGE_NAME)))
            .thenReturn(Duration.ofHours(COPC_PERSIST_DURATION));
        when(timeoutProperties.getEhrExtractWeighting()).thenReturn(1);
        when(timeoutProperties.getCopcWeighting()).thenReturn(1);
        when(outboundMessageUtil.parseFromAsid(any())).thenReturn("");
        when(outboundMessageUtil.parseMessageRef(any())).thenReturn("");
        when(outboundMessageUtil.parseToAsid(any())).thenReturn("");
        when(outboundMessageUtil.parseToOdsCode(any())).thenReturn("");

        // inbound messages
        when(mockRequest.getInboundMessage())
            .thenReturn(INBOUND_MESSAGE_STRING);
        when(mockRequest2.getInboundMessage())
            .thenReturn(INBOUND_MESSAGE_STRING_TWO);
        when(inboundMessageUtil.readMessage(INBOUND_MESSAGE_STRING))
            .thenReturn(mockInboundMessage);
        when(inboundMessageUtil.readMessage(INBOUND_MESSAGE_STRING_TWO))
            .thenReturn(mockInboundMessage2);
    }

    @Test
    public void When_CheckForTimeouts_WithTimeout_Expect_SendNACKMessageHandlerIsCalled() {
        String conversationId = UUID.randomUUID().toString();
        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_AGO, 0, conversationId);
        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithEhrExtractTranslatedTimeout_Expect_NackCode99() {
        String conversationId = UUID.randomUUID().toString();
        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_TRANSLATED, TEN_DAYS_AGO, 0, conversationId);
        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(nackMessageData.capture());

        assertThat(nackMessageData.getValue().getNackReason()).isEqualTo(UNEXPECTED_CONDITION);
    }

    @ParameterizedTest
    @MethodSource("inProgressRequestsWithAttachments")
    public void When_CheckForTimeouts_WithMigrationWithAttachmentsTimeout_Expect_NackCode31(MigrationStatus migrationStatus) {
        String conversationId = UUID.randomUUID().toString();
        callCheckForTimeoutsWithOneRequest(migrationStatus, TEN_DAYS_AGO, 0, conversationId);
        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(nackMessageData.capture());

        assertThat(nackMessageData.getValue().getNackReason()).isEqualTo(LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED);
    }

    @ParameterizedTest
    @MethodSource("preEhrParsedRequests")
    void When_CheckForTimeouts_With_PreEhrParsedRequests_Expect_EhrRequestTimeout(MigrationStatus migrationStatus) {
        String conversationId = UUID.randomUUID().toString();
        MigrationStatusLog statusLog = MigrationStatusLog.builder()
            .date(OffsetDateTime.now().minusDays(TEN_DAYS))
            .build();
        List<PatientMigrationRequest> requests = List.of(mockRequest);

        when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(migrationStatus))))
            .thenReturn(requests);
        when(dateUtils.getCurrentOffsetDateTime()).thenReturn(OffsetDateTime.now());
        when(mockRequest.getConversationId()).thenReturn(conversationId);
        when(migrationStatusLogService.getLatestMigrationStatusLog(conversationId)).thenReturn(statusLog);

        ehrTimeoutHandler.checkForTimeouts();

        verify(sendNACKMessageHandler, times(0)).prepareAndSendMessage(any());

        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(ERROR_REQUEST_TIMEOUT, conversationId, null, null);
    }

    @Test
    public void When_CheckForTimeouts_WithNackFailsToSend_Expect_MigrationLogNotUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(false);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_AGO, 0, conversationId);
        verify(migrationStatusLogService, times(0))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_TIMEOUT.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithSendNackThrows_Expect_MigrationLogNotUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenThrow(MhsServerErrorException.class);

        assertThatThrownBy(
                () -> callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_AGO, 0, conversationId))
            .isInstanceOf(MhsServerErrorException.class);

        verify(migrationStatusLogService, times(0))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_TIMEOUT.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithTimeout_Expect_MigrationLogUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_AGO, 0, conversationId);
        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithTimeoutAndCOPCReceived_Expect_MigrationLogUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(COPC_MESSAGE_RECEIVED, TEN_DAYS_AGO, 2, conversationId);
        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithTimeoutAndCOPCProcessing_Expect_MigrationLogUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(COPC_MESSAGE_PROCESSING, TEN_DAYS_AGO, 2, conversationId);
        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithTimeoutAndCOPCAcknowledged_Expect_MigrationLogUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(COPC_ACKNOWLEDGED, TEN_DAYS_AGO, 2, conversationId);
        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithTimeoutAndEhrExtractProcessing_Expect_MigrationLogUpdated() {
        String conversationId = UUID.randomUUID().toString();

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_AGO, 2, conversationId);
        verify(migrationStatusLogService, times(1))
            .addMigrationStatusLog(LARGE_MESSAGE_TIMEOUT.getMigrationStatus(),
                                   conversationId,
                                   null,
                                   LARGE_MESSAGE_ATTACHMENTS_NOT_RECEIVED.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithoutTimeout_Expect_SendNACKMessageHandlerIsNotCalled() {
        String conversationId = UUID.randomUUID().toString();
        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, TEN_DAYS_TIME, 0, conversationId);
        verify(sendNACKMessageHandler, times(0)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithAttachments_Expect_SendNACKMessageHandlerIsCalled() {
        String conversationId = UUID.randomUUID().toString();
        callCheckForTimeoutsWithOneRequest(CONTINUE_REQUEST_ACCEPTED, TEN_DAYS_AGO, 1, conversationId);
        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithCollectionAndTwoTimeouts_Expect_SendNACKMessageHandlerCalledTwice() {
        callCheckForTimeoutWithTwoRequests(TEN_DAYS_AGO, TEN_DAYS_AGO);
        verify(sendNACKMessageHandler, times(2)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithCollectionAndOneTimeout_Expect_SendNACKMessageHandlerCalledOnce() {
        callCheckForTimeoutWithTwoRequests(TEN_DAYS_TIME, TEN_DAYS_AGO);
        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithCollectionAndNoTimeouts_Expect_SendNackMessageHandlerNotCalled() {
        callCheckForTimeoutWithTwoRequests(TEN_DAYS_TIME, TEN_DAYS_TIME);
        verify(sendNACKMessageHandler, times(0)).prepareAndSendMessage(any());
    }

    @Test
    public void When_CheckForTimeouts_WithSdsRetrievalException_Expect_MigrationLogNotUpdated() {
        List<PatientMigrationRequest> requests = List.of(mockRequest);
        when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(EHR_EXTRACT_PROCESSING))))
            .thenReturn(requests);
        when(persistDurationService.getPersistDurationFor(any(), eq(EHR_EXTRACT_MESSAGE_NAME)))
            .thenThrow(new SdsRetrievalException("Test exception"));

        ehrTimeoutHandler.checkForTimeouts();

        verify(migrationStatusLogService, times(0)).addMigrationStatusLog(any(), any(), isNull(), anyString());
    }

    @Test
    public void When_CheckForTimeouts_WithJsonProcessingException_Expect_MigrationLogUpdated() throws JsonProcessingException {
        String conversationId = UUID.randomUUID().toString();
        List<PatientMigrationRequest> requests = List.of(mockRequest);
        when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(EHR_EXTRACT_TRANSLATED))))
            .thenReturn(requests);
        when(mockRequest.getConversationId()).thenReturn(conversationId);

        doThrow(JsonProcessingException.class).when(inboundMessageUtil).readMessage(any());

        ehrTimeoutHandler.checkForTimeouts();

        verify(migrationStatusLogService, times(1))
                                    .addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR,
                                                           conversationId,
                                                           null,
                                                           UNEXPECTED_CONDITION.getCode());
    }

    @Test
    public void When_CheckForTimeouts_WithSAXException_Expect_MigrationLogUpdated() throws SAXException, JsonProcessingException {
        String conversationId = UUID.randomUUID().toString();
        List<PatientMigrationRequest> requests = List.of(mockRequest);
        when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(EHR_EXTRACT_TRANSLATED))))
            .thenReturn(requests);
        when(mockRequest.getConversationId()).thenReturn(conversationId);
        when(inboundMessageUtil.readMessage(any())).thenReturn(mockInboundMessage);

        doThrow(SAXException.class).when(inboundMessageUtil).parseMessageTimestamp(any());

        ehrTimeoutHandler.checkForTimeouts();

        verify(migrationStatusLogService, times(1))
                                    .addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR,
                                                           conversationId,
                                                           null,
                                                           UNEXPECTED_CONDITION_CODE);
    }

    @Test
    public void When_CheckForTimeouts_WithDateTimeParseException_Expect_MigrationLogUpdated() throws SAXException, JsonProcessingException {
        String conversationId = UUID.randomUUID().toString();
        List<PatientMigrationRequest> requests = List.of(mockRequest);
        when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(EHR_EXTRACT_TRANSLATED))))
            .thenReturn(requests);
        when(mockRequest.getConversationId()).thenReturn(conversationId);
        when(inboundMessageUtil.readMessage(any())).thenReturn(mockInboundMessage);

        doThrow(DateTimeParseException.class).when(inboundMessageUtil).parseMessageTimestamp(any());

        ehrTimeoutHandler.checkForTimeouts();

        verify(migrationStatusLogService, times(1))
                                                 .addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR,
                                                                        conversationId,
                                                                        null,
                                                                        UNEXPECTED_CONDITION_CODE);
    }

    @Test
    public void When_MigrationTimeoutOverrideIsSet_Expect_TimeoutIsDeterminedByOverride() {
        String conversationId = UUID.randomUUID().toString();
        long overrideTimeoutSeconds = Duration.ofDays(2).getSeconds();

        when(timeoutProperties.isMigrationTimeoutOverride()).thenReturn(true);

        // Simulate a message timestamp older than the override timeout (should trigger NACK)
        ZonedDateTime messageTimestamp = ZonedDateTime.now().minusSeconds(overrideTimeoutSeconds + ONE_SECOND);

        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, messageTimestamp, 0, conversationId);

        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(any());
    }

    @Test
    public void When_MigrationTimeoutOverrideIsSetAndTimeoutNotReached_Expect_NoNackSent() {
        String conversationId = UUID.randomUUID().toString();
        long overrideTimeoutSeconds = Duration.ofDays(2).getSeconds();

        when(timeoutProperties.isMigrationTimeoutOverride()).thenReturn(true);

        // Simulate a message timestamp within the override timeout (should NOT trigger NACK)
        ZonedDateTime messageTimestamp = ZonedDateTime.now().minusSeconds(overrideTimeoutSeconds - SIXTY_SECONDS);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, messageTimestamp, 0, conversationId);

        verify(sendNACKMessageHandler, times(0)).prepareAndSendMessage(any());
    }

    @Test
    public void When_MigrationTimeoutOverrideIsFalse_WithAttachments_Expect_WeightedTimeoutCalculationAndNackSent() {
        String conversationId = UUID.randomUUID().toString();
        ZonedDateTime messageTimestamp = ZonedDateTime.now().minusDays(TEN_DAYS);

        when(timeoutProperties.isMigrationTimeoutOverride()).thenReturn(false);
        when(sendNACKMessageHandler.prepareAndSendMessage(any())).thenReturn(true);
        when(patientAttachmentLogService.countAttachmentsForMigrationRequest(mockRequest.getId())).thenReturn(2L);

        callCheckForTimeoutsWithOneRequest(EHR_EXTRACT_PROCESSING, messageTimestamp, 2, conversationId);

        verify(sendNACKMessageHandler, times(1)).prepareAndSendMessage(any());
        verify(timeoutProperties, times(1)).getEhrExtractWeighting();
        verify(timeoutProperties, times(1)).getCopcWeighting();
    }


    private void callCheckForTimeoutsWithOneRequest(MigrationStatus migrationStatus, ZonedDateTime requestTimestamp,
        long numberOfAttachments, String conversationId) {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);

        try {

            // Arrange

            setupMocks();

            // mock static method
            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(any(), eq(RCMRIN030000UKMessage.class))
            ).thenReturn(mockedPayload);

            // request
            List<PatientMigrationRequest> requests = List.of(mockRequest);
            when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(migrationStatus))))
                .thenReturn(requests);

            // timestamp
            when(mockInboundMessage.getEbXML())
                .thenReturn(EBXML_STRING);
            when(inboundMessageUtil.parseMessageTimestamp(EBXML_STRING))
                .thenReturn(requestTimestamp);
            // number of attachments
            when(patientAttachmentLogService.countAttachmentsForMigrationRequest(mockRequest.getId())).thenReturn(numberOfAttachments);
            // random conversation id for mocked request
            when(mockRequest.getConversationId()).thenReturn(conversationId);

            // Act

            ehrTimeoutHandler.checkForTimeouts();

        } catch (JsonProcessingException | SAXException e) {
            throw new RuntimeException(e);
        } finally {
            mockedXmlUnmarshall.close();
        }
    }

    private void callCheckForTimeoutWithTwoRequests(ZonedDateTime firstRequestTimestamp, ZonedDateTime secondRequestTimestamp) {

        MockedStatic<XmlUnmarshallUtil> mockedXmlUnmarshall = Mockito.mockStatic(XmlUnmarshallUtil.class);

        try {

            // Arrange

            setupMocks();

            // mock static method
            mockedXmlUnmarshall.when(
                () -> XmlUnmarshallUtil.unmarshallString(any(), eq(RCMRIN030000UKMessage.class))
            ).thenReturn(mockedPayload);

            // requests
            List<PatientMigrationRequest> requests = List.of(mockRequest, mockRequest2);
            when(migrationRequestService.getMigrationRequestsByMigrationStatusIn(argThat(list -> list.contains(EHR_EXTRACT_TRANSLATED))))
                .thenReturn(requests);

            // timestamps
            when(mockInboundMessage.getEbXML())
                .thenReturn(EBXML_STRING);
            when(mockInboundMessage2.getEbXML())
                .thenReturn(EBXML_STRING_TWO);
            when(inboundMessageUtil.parseMessageTimestamp(EBXML_STRING))
                .thenReturn(firstRequestTimestamp);
            when(inboundMessageUtil.parseMessageTimestamp(EBXML_STRING_TWO))
                .thenReturn(secondRequestTimestamp);

            // number of attachments
            when(patientAttachmentLogService.countAttachmentsForMigrationRequest(anyInt())).thenReturn((long) 0);

            // random conversation id for mocked request
            when(mockRequest.getConversationId()).thenReturn(UUID.randomUUID().toString());
            when(mockRequest2.getConversationId()).thenReturn(UUID.randomUUID().toString());

            // Act

            ehrTimeoutHandler.checkForTimeouts();

        } catch (JsonProcessingException | SAXException e) {
            throw new RuntimeException(e);
        } finally {
            mockedXmlUnmarshall.close();
        }
    }
}
