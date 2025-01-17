package uk.nhs.adaptors.pss.translator;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.connector.service.PatientAttachmentLogService;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_MESSAGE_PROCESSING;
import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;

import java.util.List;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext
@AutoConfigureMockMvc
public class COPCHandlingIT extends BaseEhrHandler {

    private static final String DECODED_MESSAGES_DIRECTORY = "/json/LargeMessage/decoded-large-attachments/";

    @Autowired
    private PatientAttachmentLogService patientAttachmentLogService;

    @Test
    public void handleCOPCAndSaveMessageIdInDatabase() {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_3/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/Scenario_3/copc.json");

        // ideally we should be checking for processing message here, but can fail due to database timing issues.
        await().until(this::isEhrMigrationCompleted);

        var migrationStatusLogs = getMigrationStatusLogService().getMigrationStatusLogs(getConversationId());

        var migrationStatusLog = migrationStatusLogs
                .stream()
                .filter(log -> log.getMigrationStatus() == COPC_MESSAGE_PROCESSING)
                .findFirst();

        Assertions.assertNotNull(migrationStatusLog);
        Assertions.assertNotNull(migrationStatusLog.get().getMessageId());
        Assertions.assertNotEquals(migrationStatusLog.get().getMessageId(), StringUtils.EMPTY);
    }

    @Test
    public void handleCOPCMessageWithMissingAttachment() {
        sendInboundMessageToQueue("/json/LargeMessage/NewError/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/NewError/copc_error_missing_attachment.json");

        await().untilAsserted(() -> assertThatMigrationStatus().isEqualTo(MigrationStatus.ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED));

        var migrationStatusLog = getMigrationStatusLogService().getLatestMigrationStatusLog(getConversationId());

        Assertions.assertEquals(migrationStatusLog.getMigrationStatus().toString(), "ERROR_LRG_MSG_ATTACHMENTS_NOT_RECEIVED");
    }

    @Test
    public void When_HandleCopc_With_DecodedLargeAttachment_Expect_FragmentsMerged() {
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc_index.json");
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc0.json");
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc1.json");

        await().until(this::isEhrMigrationCompleted);

        var copc1Log = patientAttachmentLogService
            .findAttachmentLog("bb100d3c-cdce-4f04-b41f-972da0ddb6a9", getConversationId());

        var copc0Log = patientAttachmentLogService
            .findAttachmentLog("608dbef8-f82a-44a1-a29c-64acc7c2173c", getConversationId());

        var mergedAttachmentLog = patientAttachmentLogService
            .findAttachmentLog("6dca97da-6f9d-4ed5-add4-cedecd80fe9d", getConversationId());

        assertThat(copc1Log.getIsBase64()).isFalse();
        assertThat(copc0Log.getIsBase64()).isFalse();

        assertThat(mergedAttachmentLog.getIsBase64()).isFalse();
        assertThat(mergedAttachmentLog.getUploaded()).isTrue();
    }

    @Test
    public void When_HandleCopc_With_EncodedLargeAttachment_Expect_FragmentsMerged() {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_4/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/Scenario_4/copc_index.json");
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_4/copc0.json");
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_4/copc1.json");

        await().until(this::isEhrMigrationCompleted);

        var copc1Log = patientAttachmentLogService
            .findAttachmentLog("24AED038-FF3E-466D-820A-AE1B334D68EE", getConversationId());

        var copc0Log = patientAttachmentLogService
            .findAttachmentLog("28B31-4245-4AFC-8DA2-8A40623A5101", getConversationId());

        var mergedAttachmentLog = patientAttachmentLogService
            .findAttachmentLog("E39E79A2-FA96-48FF-9373-7BBCB9D036E7", getConversationId());

        assertThat(copc1Log.getIsBase64()).isTrue();
        assertThat(copc0Log.getIsBase64()).isTrue();

        assertThat(mergedAttachmentLog.getIsBase64()).isTrue();
        assertThat(mergedAttachmentLog.getUploaded()).isTrue();
    }

    @Test
    public void When_HandleCopc_With_DecodedLargeAttachmentAndOutOfOrder_Expect_FragmentsMerged() {
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc0.json");
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc1.json");
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message/copc_index.json");


        await().until(this::isEhrMigrationCompleted);

        var copc1Log = patientAttachmentLogService
            .findAttachmentLog("bb100d3c-cdce-4f04-b41f-972da0ddb6a9", getConversationId());

        var copc0Log = patientAttachmentLogService
            .findAttachmentLog("608dbef8-f82a-44a1-a29c-64acc7c2173c", getConversationId());

        var mergedAttachmentLog = patientAttachmentLogService
            .findAttachmentLog("6dca97da-6f9d-4ed5-add4-cedecd80fe9d", getConversationId());

        assertThat(copc1Log.getIsBase64()).isFalse();
        assertThat(copc0Log.getIsBase64()).isFalse();

        assertThat(mergedAttachmentLog.getIsBase64()).isFalse();
        assertThat(mergedAttachmentLog.getUploaded()).isTrue();
    }

    @Test
    public void When_HandleCOPC_With_ManifestWithDecodedInlineFragment_Expect_FragmentsMerged() {
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message-with-cid/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message-with-cid/copc_index.json");
        sendInboundMessageToQueue(DECODED_MESSAGES_DIRECTORY + "index-message-with-cid/copc0.json");

        await().until(this::isEhrMigrationCompleted);

        List<PatientAttachmentLog> attachmentLogs = patientAttachmentLogService.findAttachmentLogs(getConversationId());

        var indexFragmentLog = attachmentLogs.stream()
            .filter(attachmentLog -> attachmentLog.getMid().startsWith("ADAPTOR_GENERATED_"))
            .findFirst()
            .orElseThrow();

        var copc0FragmentLog = attachmentLogs.stream()
            .filter(attachmentLog -> attachmentLog.getMid().equals("06137384-9f73-4723-a101-4f83cff7c72e"))
            .findFirst()
            .orElseThrow();


        var mergedAttachmentLog = attachmentLogs.stream()
            .filter(attachmentLog -> attachmentLog.getMid().equals("8c73ac84-57cc-4f11-8f0c-e0b2e9cf31fe"))
            .findFirst()
            .orElseThrow();

        assertThat(indexFragmentLog.getIsBase64()).isFalse();
        assertThat(copc0FragmentLog.getIsBase64()).isFalse();

        assertThat(mergedAttachmentLog.getIsBase64()).isFalse();
        assertThat(mergedAttachmentLog.getUploaded()).isTrue();
    }

    private boolean hasContinueMessageBeenReceived() {
        var migrationStatusLog = getMigrationStatusLogService().getLatestMigrationStatusLog(getConversationId());
        return CONTINUE_REQUEST_ACCEPTED.equals(migrationStatusLog.getMigrationStatus());
    }
}