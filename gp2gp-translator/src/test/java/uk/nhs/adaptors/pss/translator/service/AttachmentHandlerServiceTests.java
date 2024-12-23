package uk.nhs.adaptors.pss.translator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;

@ExtendWith(MockitoExtension.class)
public class AttachmentHandlerServiceTests {

    private static String conversationId;
    private static String messageId1;
    private static String messageId2;

    private static final int MESSAGE_1_LENGTH = 4040;
    private static final int MESSAGE_2_LENGTH = 220;

    @InjectMocks
    private AttachmentHandlerService attachmentHandlerService;

    @BeforeAll
    public static void setup() {
        conversationId = UUID.randomUUID().toString();
        messageId1 = UUID.randomUUID().toString();
        messageId2 = UUID.randomUUID().toString();
    }

    @Test
    public void When_BuildInboundAttachmentsFromLogs_With_EncodedAttachments_Expect_IsBase64HasCorrectValue() {

        List<PatientAttachmentLog> patientAttachmentLogs = getPatientAttachmentLogs(messageId1, messageId2);
        List<String> payloads = List.of(
            "test payload",
            "test payload 2"
        );

        List<InboundMessage.Attachment> attachmentsList = attachmentHandlerService
            .buildInboundAttachmentsFromAttachmentLogs(patientAttachmentLogs, payloads, conversationId);

        assertThat(attachmentsList.getFirst().getIsBase64()).isEqualTo("true");
        assertThat(attachmentsList.get(1).getIsBase64()).isEqualTo("false");

    }

    private List<PatientAttachmentLog> getPatientAttachmentLogs(String messageId1, String messageId2) {
        return List.of(
                PatientAttachmentLog.builder()
                    .mid(messageId1)
                    .filename("08A8D3FE-80DC-47C1-B0F4-741BBE3D2535.pdf")
                    .parentMid(null)
                    .contentType("application/pdf")
                    .compressed(false)
                    .largeAttachment(false)
                    .originalBase64(true)
                    .lengthNum(MESSAGE_1_LENGTH)
                    .isBase64(true)
                    .skeleton(false)
                    .build(),
                PatientAttachmentLog.builder()
                    .mid(messageId2)
                    .filename("E0AE6DA2-AA21-444B-BEA5-3E48F368A9DF.xml")
                    .parentMid(null)
                    .contentType("application/xml")
                    .compressed(false)
                    .largeAttachment(false)
                    .originalBase64(true)
                    .lengthNum(MESSAGE_2_LENGTH)
                    .isBase64(false)
                    .skeleton(false)
                    .build()
            );
    }
}
