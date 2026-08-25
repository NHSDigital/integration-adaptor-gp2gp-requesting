package uk.nhs.adaptors.pss.translator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.adaptors.common.enums.ConfirmationResponse;
import uk.nhs.adaptors.common.model.AcknowledgeRecordMessage;
import uk.nhs.adaptors.common.util.FileUtil;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKReason;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.ACCEPTED;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.FAILED_TO_INTEGRATE;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.NON_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.SUPPRESSED;
import static uk.nhs.adaptors.common.enums.QueueMessageType.ACKNOWLEDGE_RECORD;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.ABA_EHR_EXTRACT_SUPPRESSED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.CLINICAL_SYSTEM_INTEGRATION_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT;

@ExtendWith(MockitoExtension.class)
public class AcknowledgeRecordServiceTest {

    private static final String STRUCTURED_RECORD_PAYLOAD_XML_PATH = "/xml/RCMRIN030000UK06_LARGE_MSG/payload.xml";
    private static final String PAYLOAD = FileUtil.readResourceAsString(STRUCTURED_RECORD_PAYLOAD_XML_PATH);
    private static final String INVALID_ORIGINAL_MESSAGE = "NotAValidMessage";
    private static final String ORIGINAL_MESSAGE_VALUE = "originalMessage";
    private static final String CONVERSATION_ID_VALUE = UUID.randomUUID().toString();
    private static final Map<ConfirmationResponse, NACKReason> REASONS = Map.of(
            ABA_INCORRECT_PATIENT, ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT,
            NON_ABA_INCORRECT_PATIENT, NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT,
            FAILED_TO_INTEGRATE, CLINICAL_SYSTEM_INTEGRATION_FAILURE,
            SUPPRESSED, ABA_EHR_EXTRACT_SUPPRESSED
    );

    @Mock
    private NackAckPrepInterface preparationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InboundMessage inboundMessage;

    @InjectMocks
    private AcknowledgeRecordService acknowledgeRecordService;

    @Test
    public void prepareAndSendAcknowledgeMessageWhenOriginalMessageMissingShouldReturnFalse() {

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .conversationId(CONVERSATION_ID_VALUE)
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(ConfirmationResponse.ACCEPTED)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertFalse(result);
    }

    @Test
    public void prepareAndSendAcknowledgeMessageWhenCannotParseOriginalMessageShouldReturnFalse() {
        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .conversationId(CONVERSATION_ID_VALUE)
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(ConfirmationResponse.ACCEPTED)
                .originalMessage(INVALID_ORIGINAL_MESSAGE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertFalse(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void prepareAndSendAcknowledgeMessageWhenConversationIdIsNullOrEmptyShouldReturnFalse(
            String conversationId) {

        setupMocks(PAYLOAD);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(ConfirmationResponse.ACCEPTED)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(conversationId)
                .build();


        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void prepareAndSendAcknowledgementMessageWhenAcceptedReturnsPreparationServiceResponse(
            boolean expectedResponse) {

        setupMocks(PAYLOAD);
        when(preparationService.sendAckMessage(any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE)))
                .thenReturn(expectedResponse);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(ACCEPTED)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(CONVERSATION_ID_VALUE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertEquals(expectedResponse, result);
        verify(preparationService, times(1))
                .sendAckMessage(any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void prepareAndSendAcknowledgementMessageWhenSuppressedReturnsPreparationServiceResponse(
            boolean expectedResponse) {

        var expectedNackReason = REASONS.get(SUPPRESSED);
        setupMocks(PAYLOAD);
        when(preparationService.sendNackMessage(
                eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE)))
                .thenReturn(expectedResponse);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(SUPPRESSED)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(CONVERSATION_ID_VALUE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertEquals(expectedResponse, result);
        verify(preparationService, times(1))
                .sendNackMessage(eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void prepareAndSendAcknowledgementMessageWhenAbaIncorrectPatientReturnsPreparationServiceResponse(
            boolean expectedResponse) {

        var expectedNackReason = REASONS.get(ABA_INCORRECT_PATIENT);
        setupMocks(PAYLOAD);
        when(preparationService.sendNackMessage(
                eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE)))
                .thenReturn(expectedResponse);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(ABA_INCORRECT_PATIENT)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(CONVERSATION_ID_VALUE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertEquals(expectedResponse, result);
        verify(preparationService, times(1))
                .sendNackMessage(eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void prepareAndSendAcknowledgementMessageWhenNonAbaIncorrectPatientReturnsPreparationServiceResponse(
            boolean expectedResponse) {

        var expectedNackReason = REASONS.get(NON_ABA_INCORRECT_PATIENT);
        setupMocks(PAYLOAD);
        when(preparationService.sendNackMessage(
                eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE)))
                .thenReturn(expectedResponse);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(NON_ABA_INCORRECT_PATIENT)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(CONVERSATION_ID_VALUE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertEquals(expectedResponse, result);
        verify(preparationService, times(1))
                .sendNackMessage(eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void prepareAndSendAcknowledgementMessageWhenFailedToIntegrateReturnsPreparationServiceResponse(
            boolean expectedResponse) {

        var expectedNackReason = REASONS.get(FAILED_TO_INTEGRATE);
        setupMocks(PAYLOAD);
        when(preparationService.sendNackMessage(
                eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE)))
                .thenReturn(expectedResponse);

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .messageType(ACKNOWLEDGE_RECORD)
                .confirmationResponse(FAILED_TO_INTEGRATE)
                .originalMessage(ORIGINAL_MESSAGE_VALUE)
                .conversationId(CONVERSATION_ID_VALUE)
                .build();

        var result = acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage);

        assertEquals(expectedResponse, result);
        verify(preparationService, times(1))
                .sendNackMessage(eq(expectedNackReason), any(RCMRIN030000UKMessage.class), eq(CONVERSATION_ID_VALUE));
    }

    @SneakyThrows
    private void setupMocks(String payload) {
        when(objectMapper.readValue(ORIGINAL_MESSAGE_VALUE, InboundMessage.class))
                .thenReturn(inboundMessage);
        when(inboundMessage.getPayload())
                .thenReturn(payload);
    }
}
