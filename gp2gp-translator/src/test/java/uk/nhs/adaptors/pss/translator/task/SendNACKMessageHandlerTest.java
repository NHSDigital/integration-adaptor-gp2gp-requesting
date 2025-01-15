package uk.nhs.adaptors.pss.translator.task;

import static java.util.UUID.randomUUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.mhs.MhsRequestBuilder;
import uk.nhs.adaptors.pss.translator.mhs.model.OutboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.service.ApplicationAcknowledgementMessageService;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.service.MhsClientService;

@ExtendWith(MockitoExtension.class)
public class SendNACKMessageHandlerTest {

    private static final String TEST_MESSAGE_REF = randomUUID().toString();
    private static final String TEST_CONVERSATION_ID = randomUUID().toString();
    private static final String TEST_TO_ODS = "1234";
    private static final String TEST_TO_ASID = "5678";
    private static final String TEST_FROM_ASID = "98765";
    private static final String TEST_MESSAGE_ID = "test-message-id";

    @Mock
    private MhsClientService mhsClientService;

    @Mock
    private ApplicationAcknowledgementMessageService messageService;

    @Mock
    private MhsRequestBuilder requestBuilder;

    @Mock
    private WebClient.RequestHeadersSpec request;
    @Mock
    private IdGeneratorService idGeneratorService;

    @InjectMocks
    private SendNACKMessageHandler messageHandler;

    private NACKMessageData messageData;

    @BeforeEach
    public void setup() {
        messageData = NACKMessageData.builder()
            .messageRef(TEST_MESSAGE_REF)
            .conversationId(TEST_CONVERSATION_ID)
            .toOdsCode(TEST_TO_ODS)
            .toAsid(TEST_TO_ASID)
            .fromAsid(TEST_FROM_ASID)
            .nackReason(NACKReason.LARGE_MESSAGE_GENERAL_FAILURE)
            .build();

        when(idGeneratorService.generateUuid()).thenReturn(TEST_MESSAGE_ID);

        when(requestBuilder.buildSendACKRequest(eq(TEST_CONVERSATION_ID), eq(TEST_TO_ODS), any(OutboundMessage.class),
            eq(TEST_MESSAGE_ID.toUpperCase())))
            .thenReturn(request);
    }

    @Test
    public void When_SendMessage_WithSuccess_Expect_TrueIsReturned() {
        assertTrue(messageHandler.prepareAndSendMessage(messageData));
    }

    @Test
    public void When_SendMessage_WithClientError_Expect_FalseIsReturned() {
        when(mhsClientService.send(request)).thenThrow(
            new WebClientResponseException(
                HttpStatus.BAD_REQUEST.value(),
                "BAD REQUEST",
                new HttpHeaders(),
                new byte[] {},
                Charset.defaultCharset())
        );

        assertFalse(messageHandler.prepareAndSendMessage(messageData));
    }

    @Test
    public void When_SendMessage_WithServerError_Expect_ExceptionThrown() {
        when(mhsClientService.send(request)).thenThrow(
            new WebClientResponseException(
                INTERNAL_SERVER_ERROR.value(),
                INTERNAL_SERVER_ERROR.getReasonPhrase(),
                new HttpHeaders(),
                new byte[] {},
                Charset.defaultCharset())
        );

        assertThatThrownBy(() -> messageHandler.prepareAndSendMessage(messageData))
            .isInstanceOf(MhsServerErrorException.class);
    }

    @Test
    public void When_PrepareAndSendRequest_Expect_MessageBuilderCalledWithCorrectParams() {
        messageHandler.prepareAndSendMessage(messageData);

        verify(messageService).buildNackMessage(messageData, TEST_MESSAGE_ID.toUpperCase());
    }

}
