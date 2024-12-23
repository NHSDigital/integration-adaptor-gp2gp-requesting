package uk.nhs.adaptors.pss.translator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.adaptors.common.enums.ConfirmationResponse;
import uk.nhs.adaptors.common.model.AcknowledgeRecordMessage;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKReason;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static uk.nhs.adaptors.common.enums.QueueMessageType.ACKNOWLEDGE_RECORD;
import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class, MockitoExtension.class})
public class AcknowledgeRecordServiceIT {

    public static final int SIZE_22_MB = 22 * 1024 * 1024;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AcknowledgeRecordService acknowledgeRecordService;

    @MockBean
    private NackAckPrepInterface nackAckPrepInterface;

    private static final String CONVERSATION_ID_VALUE = UUID.randomUUID().toString();
    private static final String EBXML_PART_PATH = "/xml/RCMR_IN030000UK06/ebxml_part.xml";
    @Getter
    protected static final String CONVERSATION_ID_PLACEHOLDER = "{{conversationId}}";

    @Test
    public void prepareAndSendAcknowledgeMessageDoesNotThrowExceptionWhenMessageIsOver30MbTest() {

        var acknowledgeRecordMessage = AcknowledgeRecordMessage.builder()
            .conversationId(CONVERSATION_ID_VALUE)
            .messageType(ACKNOWLEDGE_RECORD)
            .confirmationResponse(ConfirmationResponse.FAILED_TO_INTEGRATE)
            .originalMessage(parseMessageToString(createInboundMessage("/e2e-mapping/input-xml/PWTP2.xml")))
            .build();

        doReturn(true).when(nackAckPrepInterface).sendNackMessage(any(NACKReason.class),
                                                                              any(RCMRIN030000UKMessage.class),
                                                                              anyString());

        assertTrue(() -> acknowledgeRecordService.prepareAndSendAcknowledgementMessage(acknowledgeRecordMessage));
    }

    private InboundMessage createInboundMessage(String payloadPartPath) {
        var ebXml = readResourceAsString(EBXML_PART_PATH).replace(CONVERSATION_ID_PLACEHOLDER, CONVERSATION_ID_VALUE);

        var inboundMessage = new InboundMessage();
        inboundMessage.setPayload(readResourceAsString(payloadPartPath));
        inboundMessage.setEbXML(ebXml);
        inboundMessage.setAttachments(
            List.of(InboundMessage.Attachment.builder()
                                             .contentType("application/text")
                                             .payload("1".repeat(SIZE_22_MB))
                                             .build()));
        return inboundMessage;
    }

    @SneakyThrows
    private String parseMessageToString(InboundMessage inboundMessage) {
        return objectMapper.writeValueAsString(inboundMessage);
    }

    private String sendInboundMessageAndWaitForBundle(String inputFilePath) {
        final var inboundMessage = parseMessageToString(createInboundMessage(inputFilePath));
        return inboundMessage;
    }

}
