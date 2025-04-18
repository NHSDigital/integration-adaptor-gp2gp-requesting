package uk.nhs.adaptors.pss.translator.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.adaptors.common.model.AcknowledgeRecordMessage;
import uk.nhs.adaptors.common.model.PssQueueMessage;
import uk.nhs.adaptors.common.model.TransferRequestMessage;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.pss.translator.service.AcknowledgeRecordService;

import jakarta.jms.JMSException;
import jakarta.jms.Message;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.adaptors.common.enums.QueueMessageType.ACKNOWLEDGE_RECORD;
import static uk.nhs.adaptors.common.enums.QueueMessageType.TRANSFER_REQUEST;

@ExtendWith(MockitoExtension.class)
public class QueueMessageHandlerTest {

    private static final String CONVERSATION_ID = UUID.randomUUID().toString();
    private static final String CONVERSATION_ID_UPPER = CONVERSATION_ID.toUpperCase(Locale.ROOT);

    @Mock
    private Message message;

    @Mock
    private SendEhrExtractRequestHandler sendEhrExtractRequestHandler;

    @Mock
    private AcknowledgeRecordService acknowledgeRecordService;

    @Mock
    private MDCService mdcService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private QueueMessageHandler queueMessageHandler;


    @Test
    public void handleMessageWhenSendEhrExtractRequestHandlerReturnsTrue() {
        prepareMocksForTransferRequest(true, CONVERSATION_ID_UPPER);

        boolean messageAcknowledged = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        verify(acknowledgeRecordService, never())
                .prepareAndSendAcknowledgementMessage(any(AcknowledgeRecordMessage.class));

        assertTrue(messageAcknowledged);
    }

    @Test
    public void handleMessageWhenSendEhrExtractRequestHandlerReturnsTrueWithLowercaseConversationId() {
        prepareMocksForTransferRequest(true, CONVERSATION_ID);

        boolean messageAcknowledged = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        verify(acknowledgeRecordService, never())
            .prepareAndSendAcknowledgementMessage(any(AcknowledgeRecordMessage.class));

        assertTrue(messageAcknowledged);
    }

    @Test
    public void handleMessageWhenSendEhrExtractRequestHandlerReturnsFalse() {
        prepareMocksForTransferRequest(false, CONVERSATION_ID_UPPER);

        boolean messageAcknowledged = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        assertFalse(messageAcknowledged);
    }

    @Test
    public void handleMessageWhenAcknowledgeRecordServiceReturnsTrue() {
        prepareMocksForAcknowledgeRecord(true, CONVERSATION_ID_UPPER);

        var result = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        verify(sendEhrExtractRequestHandler, never()).prepareAndSendRequest(any(TransferRequestMessage.class));
        assertTrue(result);
    }

    @Test
    public void handleMessageWhenAcknowledgeRecordServiceReturnsTrueWithLowercaseConversationId() {
        prepareMocksForAcknowledgeRecord(true, CONVERSATION_ID);

        var result = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        verify(sendEhrExtractRequestHandler, never()).prepareAndSendRequest(any(TransferRequestMessage.class));
        assertTrue(result);
    }

    @Test
    public void handleMessageWhenAcknowledgeRecordServiceReturnsFalse() {
        prepareMocksForAcknowledgeRecord(false, CONVERSATION_ID_UPPER);

        var result = queueMessageHandler.handle(message);

        verify(mdcService).applyConversationId(CONVERSATION_ID_UPPER);
        verify(sendEhrExtractRequestHandler, never()).prepareAndSendRequest(any(TransferRequestMessage.class));
        assertFalse(result);
    }

    @Test
    @SneakyThrows
    public void handleMessageWhenJMSExceptionIsThrown() {
        when(message.getBody(String.class)).thenThrow(new JMSException("not good"));
        assertFalse(queueMessageHandler.handle(message));
    }

    @SneakyThrows
    private void prepareMocksForTransferRequest(boolean prepareAndSendRequestResult, String conversationID) {
        var transferRequestMessage = TransferRequestMessage.builder()
            .conversationId(conversationID)
            .messageType(TRANSFER_REQUEST)
            .build();

        var pssQueueMessage = PssQueueMessage.builder()
                .conversationId(conversationID)
                .messageType(TRANSFER_REQUEST)
                .build();

        var transferRequestMessageString = new ObjectMapper().writeValueAsString(transferRequestMessage);

        when(message.getBody(String.class)).thenReturn(transferRequestMessageString);
        when(objectMapper.readValue(transferRequestMessageString, PssQueueMessage.class))
                .thenReturn(pssQueueMessage);
        when(objectMapper.readValue(transferRequestMessageString, TransferRequestMessage.class))
                .thenReturn(transferRequestMessage);
        when(sendEhrExtractRequestHandler.prepareAndSendRequest(transferRequestMessage)).thenReturn(prepareAndSendRequestResult);
    }

    @SneakyThrows
    private void prepareMocksForAcknowledgeRecord(boolean prepareAndSendAcknowledgementMessageResult, String conversationId) {
        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
                .conversationId(conversationId)
                .messageType(ACKNOWLEDGE_RECORD)
                .build();

        var pssQueueMessage = PssQueueMessage.builder()
                .conversationId(conversationId)
                .messageType(ACKNOWLEDGE_RECORD)
                .build();

        var acknowledgeRequestMessageString = new ObjectMapper()
                .writeValueAsString(acknowledgeRecordMessage);

        when(message.getBody(String.class))
                .thenReturn(acknowledgeRequestMessageString);
        when(objectMapper.readValue(acknowledgeRequestMessageString, PssQueueMessage.class))
                .thenReturn(pssQueueMessage);
        when(objectMapper.readValue(acknowledgeRequestMessageString, AcknowledgeRecordMessage.class))
                .thenReturn(acknowledgeRecordMessage);
        when(acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage))
                .thenReturn(prepareAndSendAcknowledgementMessageResult);
    }
}
