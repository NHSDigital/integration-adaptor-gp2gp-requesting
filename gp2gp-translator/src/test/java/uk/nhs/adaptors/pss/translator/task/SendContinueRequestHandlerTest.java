package uk.nhs.adaptors.pss.translator.task;

import static java.nio.charset.StandardCharsets.UTF_8;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.mhs.MhsRequestBuilder;
import uk.nhs.adaptors.pss.translator.mhs.model.OutboundMessage;
import uk.nhs.adaptors.pss.translator.model.ContinueRequestData;
import uk.nhs.adaptors.pss.translator.service.ContinueRequestService;
import uk.nhs.adaptors.pss.translator.service.IdGeneratorService;
import uk.nhs.adaptors.pss.translator.service.MhsClientService;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.io.IOException;
import java.nio.charset.Charset;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class SendContinueRequestHandlerTest {

    private static final String NHS_NUMBER = "9446363101";
    private static final String CONVERSATION_ID = "6E242658-3D8E-11E3-A7DC-172BDA00FA67";
    private static final String LOSING_ODS_CODE = "B83002"; //to odds code
    private static final String WINNING_ODS_CODE = "C81007"; //from odds code
    private static final String TO_ASID = "715373337545";
    private static final String FROM_ASID = "276827251543";
    private static final String MCCI_IN010000UK13_CREATIONTIME = "20220407194614";
    private static final String MESSAGE_ID = "message-id";

    @Mock
    private MhsRequestBuilder requestBuilder;

    @Mock
    private ContinueRequestService continueRequestService;

    @Mock
    private MigrationStatusLogService migrationStatusLogService;

    @Mock
    private MhsClientService mhsClientService;

    @Mock
    private ContinueRequestData data;

    @Mock
    private HttpHeaders headers;
    @Mock
    private IdGeneratorService idGeneratorService;

    @InjectMocks
    private SendContinueRequestHandler sendContinueRequestHandler;

    @BeforeEach
    public void setup() {
        when(idGeneratorService.generateUuid()).thenReturn(MESSAGE_ID);
    }

    @Test
    public void When_PrepareAndSendRequest_ToMhsAndGetError_Expect_ThrowError() {

        ContinueRequestData continueRequestData = ContinueRequestData.builder()
                .conversationId(CONVERSATION_ID)
                .fromAsid(FROM_ASID)
                .toAsid(TO_ASID)
                .nhsNumber(NHS_NUMBER)
                .fromOdsCode(WINNING_ODS_CODE)
                .toOdsCode(LOSING_ODS_CODE)
                .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
                .build();

        doThrow(new WebClientResponseException(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), headers,
            "test body".getBytes(UTF_8), UTF_8))
            .when(mhsClientService).send(any());

        assertThrows(WebClientResponseException.class, () ->
            sendContinueRequestHandler.prepareAndSendRequest(continueRequestData)
        );
    }

    @Test
    public void When_MHSClientService_SendThrowsErrors_Expect_MigrationStatusLogAddStatusIsCalledWithContinueRequestError()
            throws WebClientResponseException {

        ContinueRequestData continueRequestData = ContinueRequestData.builder()
                .conversationId(CONVERSATION_ID)
                .fromAsid(FROM_ASID)
                .toAsid(TO_ASID)
                .nhsNumber(NHS_NUMBER)
                .fromOdsCode(WINNING_ODS_CODE)
                .toOdsCode(LOSING_ODS_CODE)
                .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
                .build();

        doThrow(new WebClientResponseException(BAD_REQUEST.value(), BAD_REQUEST.getReasonPhrase(), headers,
            "test body".getBytes(UTF_8), UTF_8))
            .when(mhsClientService).send(any());

        try {
            sendContinueRequestHandler.prepareAndSendRequest(continueRequestData);
        } catch (Exception ignored) {
        }

        verify(migrationStatusLogService)
            .addMigrationStatusLog(MigrationStatus.CONTINUE_REQUEST_ERROR,
                                   CONVERSATION_ID,
                                   null,
                                   UNEXPECTED_CONDITION.getCode());
    }

    @Test
    public void When_PrepareAndSendRequest_IsCalled_Expect_NoErrors() {

        ContinueRequestData continueRequestData = ContinueRequestData
                .builder()
                .conversationId(CONVERSATION_ID)
                .fromAsid(FROM_ASID)
                .toAsid(TO_ASID)
                .nhsNumber(NHS_NUMBER)
                .fromOdsCode(WINNING_ODS_CODE)
                .toOdsCode(LOSING_ODS_CODE)
                .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
                .build();

        sendContinueRequestHandler.prepareAndSendRequest(continueRequestData);
    }

    @Test
    public void When_ParametersCorrect_Expect_MHSClientServiceSendIsCalled() {

        ContinueRequestData continueRequestData = ContinueRequestData
                .builder()
                .conversationId(CONVERSATION_ID)
                .fromAsid(FROM_ASID)
                .toAsid(TO_ASID)
                .nhsNumber(NHS_NUMBER)
                .fromOdsCode(WINNING_ODS_CODE)
                .toOdsCode(LOSING_ODS_CODE)
                .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
                .build();

        sendContinueRequestHandler.prepareAndSendRequest(continueRequestData);
        verify(mhsClientService).send(any());
    }

    @Test
    public void When_ParametersCorrect_Expect_MigrationStatusLogServiceAddMigrationStatusLogIsCalled() {

        ContinueRequestData continueRequestData = ContinueRequestData
                .builder()
                .conversationId(CONVERSATION_ID)
                .fromAsid(FROM_ASID)
                .toAsid(TO_ASID)
                .nhsNumber(NHS_NUMBER)
                .fromOdsCode(WINNING_ODS_CODE)
                .toOdsCode(LOSING_ODS_CODE)
                .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
                .build();

        sendContinueRequestHandler.prepareAndSendRequest(continueRequestData);
        verify(migrationStatusLogService).addMigrationStatusLog(MigrationStatus.CONTINUE_REQUEST_ACCEPTED, CONVERSATION_ID, null, null);
    }

    @Test
    public void When_SendRequest_WithServerError_Expect_ExceptionThrown() {
        ContinueRequestData continueRequestData = ContinueRequestData
            .builder()
            .conversationId(CONVERSATION_ID)
            .fromAsid(FROM_ASID)
            .toAsid(TO_ASID)
            .nhsNumber(NHS_NUMBER)
            .fromOdsCode(WINNING_ODS_CODE)
            .toOdsCode(LOSING_ODS_CODE)
            .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
            .build();

        when(mhsClientService.send(any())).thenThrow(
            new WebClientResponseException(
                INTERNAL_SERVER_ERROR.value(),
                INTERNAL_SERVER_ERROR.getReasonPhrase(),
                new HttpHeaders(),
                new byte[] {},
                Charset.defaultCharset())
        );

        assertThatThrownBy(() -> sendContinueRequestHandler.prepareAndSendRequest(continueRequestData))
            .isInstanceOf(MhsServerErrorException.class);
    }

    @Test
    public void When_SendRequest_Expect_BuildRequestCalledWithCorrectParams() throws IOException {
        ContinueRequestData continueRequestData = ContinueRequestData
            .builder()
            .conversationId(CONVERSATION_ID)
            .fromAsid(FROM_ASID)
            .toAsid(TO_ASID)
            .nhsNumber(NHS_NUMBER)
            .fromOdsCode(WINNING_ODS_CODE)
            .toOdsCode(LOSING_ODS_CODE)
            .mcciIN010000UK13creationTime(MCCI_IN010000UK13_CREATIONTIME)
            .build();

        String testPayload = "test-payload";
        var outboundMessage = new OutboundMessage(testPayload);

        when(continueRequestService.buildContinueRequest(any(), any())).thenReturn(testPayload);

        sendContinueRequestHandler.prepareAndSendRequest(continueRequestData);
        verify(continueRequestService).buildContinueRequest(continueRequestData, MESSAGE_ID.toUpperCase());
        verify(requestBuilder).buildSendContinueRequest(CONVERSATION_ID, LOSING_ODS_CODE, outboundMessage, MESSAGE_ID.toUpperCase());
    }
}
