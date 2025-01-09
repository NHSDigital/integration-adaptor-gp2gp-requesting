package uk.nhs.adaptors.pss.translator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.toHl7Format;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKReason;

@ExtendWith(MockitoExtension.class)
public class ApplicationAcknowledgementMessageServiceTest {

    private static final String MESSAGE_ID = "123-ABC";
    private static final String MESSAGE_REF = "8910-XYZ";
    private static final String TEST_FROM_ASID = "TEST_FROM_ASID";
    private static final String TEST_TO_ASID = "TEST_TO_ASID";
    private static final String TEST_TO_ODS = "TEST_TO_ODS";
    private static final String TEST_CONVERSATION_ID = "abcd12345";

    @Mock
    private DateUtils dateUtils;

    @InjectMocks
    private ApplicationAcknowledgementMessageService messageService;

    private NACKMessageData messageData;

    @BeforeEach
    public void setup() {
        Instant instant = Instant.now();
        when(dateUtils.getCurrentInstant()).thenReturn(instant);

        messageData = NACKMessageData.builder()
            .conversationId(TEST_CONVERSATION_ID)
            .toOdsCode(TEST_TO_ODS)
            .nackReason(NACKReason.LARGE_MESSAGE_TIMEOUT)
            .messageRef(MESSAGE_REF)
            .fromAsid(TEST_FROM_ASID)
            .toAsid(TEST_TO_ASID)
            .build();
    }

    @Test
    public void When_BuildNackMessage_WithValidTestData_Expect_NackCodeAndResponseTextAreOutput() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertAll(
            () -> assertThat(nackMessage).contains("code=\"" + NACKReason.LARGE_MESSAGE_TIMEOUT.getCode() + "\""),
            () -> assertThat(nackMessage).contains("displayName=\"" + NACKReason.LARGE_MESSAGE_TIMEOUT.getResponseText() + "\"")
        );
    }

    @Test
    public void When_BuildNackMessage_WithValidTestData_Expect_MessageIdIsSetCorrectly() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertThat(nackMessage).contains(MESSAGE_ID);
    }

    @Test
    public void When_BuildNackMessage_WithValidTestData_Expect_MessageRefIsSetCorrectly() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertThat(nackMessage).contains(MESSAGE_REF);
    }

    @Test
    public void When_BuildNackMessage_WithValidTestData_Expect_ToAsidIsSetCorrectly() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertThat(nackMessage).contains(TEST_TO_ASID);
    }

    @Test
    public void When_NackMessage_WithTestData_Expect_FromAsidIsSetCorrectly() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertThat(nackMessage).contains(TEST_FROM_ASID);
    }

    @Test
    public void When_NackMessage_WithTestData_Expect_CreationTimeIsSetCorrectly() {
        Instant instant = Instant.now();
        when(dateUtils.getCurrentInstant()).thenReturn(instant);

        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertThat(nackMessage).contains(toHl7Format(instant));
    }

    @Test
    public void When_NackMessage_WithNackCodePresent_Expect_TypeCodeSetCorrectly() {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        assertAll(
            () -> assertThat(nackMessage).contains("typeCode=\"AE\""),
            () -> assertThat(nackMessage).doesNotContain("typeCode=\"AA\"")
        );
    }

    @Test
    public void When_BuildNackMessage_WithNackCodePresent_Expect_ReasonElementIncluded() throws ParserConfigurationException, IOException,
        SAXException {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document messageXml = db.parse(new InputSource(new StringReader(nackMessage)));
        NodeList nodes = messageXml.getElementsByTagName("reason");

        assertThat(nodes.getLength()).isEqualTo(1);
    }

    @Test
    public void When_BuildNackMessage_WithNackCodePresent_Expect_ReasonHasCorrectAttribute() throws ParserConfigurationException,
        IOException, SAXException {
        String nackMessage = messageService.buildNackMessage(messageData, MESSAGE_ID);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document messageXml = db.parse(new InputSource(new StringReader(nackMessage)));
        NodeList nodes = messageXml.getElementsByTagName("reason");
        String reasonAttribute = nodes.item(0).getAttributes().item(0).toString();

        assertThat(reasonAttribute).isEqualTo("typeCode=\"RSON\"");
    }
}
