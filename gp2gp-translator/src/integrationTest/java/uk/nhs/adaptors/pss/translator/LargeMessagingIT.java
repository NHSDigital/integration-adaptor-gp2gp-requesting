package uk.nhs.adaptors.pss.translator;

import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

import static uk.nhs.adaptors.common.util.FileUtil.readResourceAsString;
import static uk.nhs.adaptors.common.enums.MigrationStatus.CONTINUE_REQUEST_ACCEPTED;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import uk.nhs.adaptors.pss.util.BaseEhrHandler;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ExtendWith({SpringExtension.class})
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
public final class LargeMessagingIT extends BaseEhrHandler {

    private LargeMessagingIT() {
        setIgnoredJsonPaths(List.of(
            "id",
            "entry[0].resource.id",
            "entry[0].resource.identifier[0].value",
            "entry[*].resource.id",
            "entry[*].resource.subject.reference",
            "entry[*].resource.patient.reference",
            "entry[*].resource.performer[0].reference",
            "entry[*].resource.content[0].attachment.title",
            "entry[*].resource.content[0].attachment.url",
            "entry[*].resource.description"
        ));
    }

    @Test
    public void handleUk06WithCidAttachment() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_1/uk06.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario1.json");
    }

    @Test
    public void handleUk06WithEMISCidAttachment() throws JSONException {
        // NIAD-2789: EMIS can send inline attachments where the attachment description contains ONLY a filename.
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_12/uk06.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario1.json");
    }

    @Test
    public void handleUk06WithCompressedCidAttachmement() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_2/uk06.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario2.json");
    }

    @Test
    public void handleUk06WithOneMidAttachment() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_3/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/Scenario_3/copc.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario3.json");
    }

    @Test
    public void handleUk06WithSkeletonAsMid() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_5/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/Scenario_5/copc.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario5.json");
    }

    @Test
    public void handleUk06WithSkeletonAsEntireRCMRInEHR() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_10/uk06.json");
        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario10.json");
    }

    @Test
    public void handleUk06WithSkeletonAsEntireRCMRInCOPC() throws JSONException {
        sendInboundMessageToQueue("/json/LargeMessage/Scenario_11/uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue("/json/LargeMessage/Scenario_11/copc.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle("/json/LargeMessage/expectedBundleScenario11.json");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ehrAndCopcMessageResourceFiles")
    public void uk06WithCopcMessages(String testName, String scenarioDirectory, String expectedBundleName) throws JSONException {
        sendInboundMessageToQueue(scenarioDirectory + "uk06.json");

        await().until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(scenarioDirectory + "copc_index.json");
        sendInboundMessageToQueue(scenarioDirectory + "copc0.json");
        sendInboundMessageToQueue(scenarioDirectory + "copc1.json");

        await().until(this::isEhrMigrationCompleted);

        verifyBundle(expectedBundleName);
    }

    /**
     * We've previously had the Adaptor consume large amounts of memory when processing 100 large attachments.
     * See PR #344 AKA NIAD-2892 for details.
     */
    @Test
    public void handleUk06withMultipleLargeCOPCMessages() {
        var ids = sendInboundMessageToQueueAndExtractMids("/json/LargeMessage/multiple-large-copc-messages/uk06.json");

        await().atMost(Duration.ofMinutes(1L)).until(this::hasContinueMessageBeenReceived);

        sendCOPCMessagesToQueue("/json/LargeMessage/multiple-large-copc-messages/copc.json", ids);

        await().atMost(Duration.ofMinutes(1L)).until(this::isEhrMigrationCompleted);
    }

    @Test
    public void handleEhrAndCopcWithNonUniqueFilenames() {
        String testDirectory = "/json/LargeMessage/non-unique-fragment-names/";

        sendInboundMessageToQueue(testDirectory + "uk06.json");

        await().atMost(Duration.ofMinutes(1L)).until(this::hasContinueMessageBeenReceived);

        sendInboundMessageToQueue(testDirectory + "copc1.json");
        sendInboundMessageToQueue(testDirectory + "copc2.json");

        await().atMost(Duration.ofMinutes(1L)).until(this::isEhrMigrationCompleted);
    }

    private static Stream<Arguments> ehrAndCopcMessageResourceFiles() {
        return Stream.of(
            Arguments.of("handleUk06WithFragmentedMids", "/json/LargeMessage/Scenario_4/",
                "/json/LargeMessage/expectedBundleScenario4.json"),
            Arguments.of("handleUk06WithMidAttachmentsWithCidAndMidCombo", "/json/LargeMessage/Scenario_6/",
                "/json/LargeMessage/expectedBundleScenario6.json"),
            Arguments.of("handleUk06WithSkeletonFragments", "/json/LargeMessage/Scenario_7/",
                "/json/LargeMessage/expectedBundleScenario7.json"),
            Arguments.of("handleUk06WithFragmentedMidCidCombo", "/json/LargeMessage/Scenario_8/",
                "/json/LargeMessage/expectedBundleScenario8.json"),
            Arguments.of("handleUk06WithOneMidAttachmentCheck", "/json/LargeMessage/Scenario_9/",
                "/json/LargeMessage/expectedBundleScenario9.json")
        );
    }

    private boolean hasContinueMessageBeenReceived() {
        var migrationStatusLog = getMigrationStatusLogService().getLatestMigrationStatusLog(getConversationId());
        return CONTINUE_REQUEST_ACCEPTED.equals(migrationStatusLog.getMigrationStatus());
    }

    private ArrayList<String> sendInboundMessageToQueueAndExtractMids(String filename) {
        var jsonMessage = fetchMessageInJsonFormat(filename);

        getMhsJmsTemplate().send(session -> session.createTextMessage(jsonMessage));

        var ids = new ArrayList<String>();
        var regex = "mid:([^\\\\]+)\\\\";
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(jsonMessage);

        while (matcher.find()) {
            ids.add(matcher.group(1));
        }

        return ids;
    }

    private void sendCOPCMessagesToQueue(String filepath, ArrayList<String> mids) {
        var template = readResourceAsString(filepath)
            .replace(CONVERSATION_ID_PLACEHOLDER, getConversationId());

        for (String mid : mids) {
            var jsonMessage = template
                .replace("{{messageId}}", mid);
            getMhsJmsTemplate().send(session -> session.createTextMessage(jsonMessage));
        }
    }
}