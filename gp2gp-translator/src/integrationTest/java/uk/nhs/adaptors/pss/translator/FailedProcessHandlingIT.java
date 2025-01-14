package uk.nhs.adaptors.pss.translator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN;
import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;
import static uk.nhs.adaptors.common.enums.MigrationStatus.ERROR_LRG_MSG_TIMEOUT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import uk.nhs.adaptors.common.enums.MigrationStatus;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.util.BaseEhrHandler;

@SpringBootTest
@ExtendWith({SpringExtension.class})
@DirtiesContext
public class FailedProcessHandlingIT extends BaseEhrHandler {

    private static final String EHR_MESSAGE_EXTRACT_PATH = "/json/LargeMessage/Scenario_3/uk06.json";
    private static final String COPC_MESSAGE_PATH = "/json/LargeMessage/Scenario_3/copc.json";

    private static final String UNEXPECTED_CONDITION_CODE = "99";
    private static final String LARGE_MESSAGE_TIMEOUT_CODE = "25";
    private static final String EHR_GENERAL_PROCESSING_ERROR_CODE = "30";
    private static final String EHR_NACK_UNKNOWN = "99";

    @Autowired
    private MigrationStatusLogService migrationStatusLogService;

    @Test
    public void When_ProcessFailedByIncumbent_With_EhrExtract_Expect_NotProcessed() {
        sendNackToQueue();

        await().until(() -> isMigrationStatus(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN));

        sendEhrExtractToQueue();

        await().until(() -> hasNackBeenSentWithCode(UNEXPECTED_CONDITION_CODE));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN);
    }

    @Test
    public void When_ProcessFailedByIncumbent_With_CopcMessage_Expect_NotProcessed() {
        sendEhrExtractToQueue();

        await().until(() -> isMigrationStatus(CONTINUE_REQUEST_ACCEPTED));

        sendNackToQueue();

        await().until(() -> isMigrationStatus(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN));

        sendCopcToQueue();

        await().until(() -> hasNackBeenSentWithCode(EHR_NACK_UNKNOWN));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_EXTRACT_REQUEST_NEGATIVE_ACK_UNKNOWN);
    }

    @Test
    public void When_ProcessFailedByNME_With_EhrExtract_Expect_NotProcessed() {
        migrationStatusLogService.addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR, getConversationId(), null, "99");

        sendEhrExtractToQueue();

        await().until(() -> hasNackBeenSentWithCode(UNEXPECTED_CONDITION_CODE));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(EHR_GENERAL_PROCESSING_ERROR);
    }

    @Test
    public void When_ProcessFailedByNme_With_CopcMessageAndTimeout_Expect_NotProcessed() {
        whenCopcSentExpectNackCode(ERROR_LRG_MSG_TIMEOUT, LARGE_MESSAGE_TIMEOUT_CODE);
    }

    @Test
    public void When_ProcessFailedByNme_With_CopcMessage_Expect_NotProcessed() {
        whenCopcSentExpectNackCode(EHR_GENERAL_PROCESSING_ERROR, EHR_GENERAL_PROCESSING_ERROR_CODE);
    }

    private void whenCopcSentExpectNackCode(MigrationStatus preCopcMigrationStatus, String nackCode) {
        sendEhrExtractToQueue();

        await().until(() -> isMigrationStatus(CONTINUE_REQUEST_ACCEPTED));

        migrationStatusLogService.addMigrationStatusLog(preCopcMigrationStatus, getConversationId(), null, null);

        sendCopcToQueue();

        await().until(() -> hasNackBeenSentWithCode(nackCode));

        var migrationStatus = migrationStatusLogService.getLatestMigrationStatusLog(getConversationId()).getMigrationStatus();

        assertThat(migrationStatus).isEqualTo(preCopcMigrationStatus);
    }

    private void sendEhrExtractToQueue() {
        sendInboundMessageToQueue(EHR_MESSAGE_EXTRACT_PATH);
    }

    private void sendCopcToQueue() {
        sendInboundMessageToQueue(COPC_MESSAGE_PATH);
    }

}
