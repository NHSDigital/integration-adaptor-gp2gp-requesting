package uk.nhs.adaptors.pss.translator.task;

import static uk.nhs.adaptors.common.enums.MigrationStatus.EHR_GENERAL_PROCESSING_ERROR;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.parser.DataFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.connector.service.PatientMigrationRequestService;
import uk.nhs.adaptors.pss.translator.amqp.JmsReader;
import uk.nhs.adaptors.pss.translator.exception.AttachmentLogException;
import uk.nhs.adaptors.pss.translator.exception.AttachmentNotFoundException;
import uk.nhs.adaptors.pss.translator.exception.BundleMappingException;
import uk.nhs.adaptors.pss.translator.exception.ConversationIdNotFoundException;
import uk.nhs.adaptors.pss.translator.exception.InlineAttachmentProcessingException;
import uk.nhs.adaptors.pss.translator.exception.MhsServerErrorException;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.service.XPathService;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.UNEXPECTED_CONDITION;

import java.text.ParseException;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class MhsQueueMessageHandler {
    private static final String EHR_EXTRACT_INTERACTION_ID06 = "RCMR_IN030000UK06";
    private static final String EHR_EXTRACT_INTERACTION_ID07 = "RCMR_IN030000UK07";
    private static final String ACKNOWLEDGEMENT_INTERACTION_ID = "MCCI_IN010000UK13";
    private static final String CONTINUE_ATTACHMENT_INTERACTION_ID = "COPC_IN000001UK01";
    private static final String CONVERSATION_ID_PATH = "/Envelope/Header/MessageHeader/ConversationId";
    private static final String INTERACTION_ID_PATH = "/Envelope/Header/MessageHeader/Action";

    private final ObjectMapper objectMapper;
    private final JmsReader jmsReader;
    private final XPathService xPathService;
    private final MDCService mdcService;
    private final EhrExtractMessageHandler ehrExtractMessageHandler;
    private final AcknowledgmentMessageHandler acknowledgmentMessageHandler;
    private final COPCMessageHandler continueMessageHandler;
    private final MigrationStatusLogService migrationStatusLogService;
    private final PatientMigrationRequestService migrationRequestService;

    public boolean handleMessage(Message message) {

        String conversationId = ""; // We need access to conversation n our catch statements

        try {
            InboundMessage inboundMessage = readMessage(message);
            Document ebXmlDocument = xPathService.parseDocumentFromXml(inboundMessage.getEbXML());
            conversationId = xPathService.getNodeValue(ebXmlDocument, CONVERSATION_ID_PATH);
            conversationId = conversationId.toUpperCase(Locale.ROOT);
            applyConversationId(conversationId);
            String interactionId = xPathService.getNodeValue(ebXmlDocument, INTERACTION_ID_PATH);

            if (!migrationRequestService.hasMigrationRequest(conversationId)) {

                throw new ConversationIdNotFoundException("Conversation ID" + conversationId
                    + "does not have a migration status log entry", conversationId);
            }

            if (ACKNOWLEDGEMENT_INTERACTION_ID.equals(interactionId)) {
                acknowledgmentMessageHandler.handleMessage(inboundMessage, conversationId);
            } else if (EHR_EXTRACT_INTERACTION_ID06.equals(interactionId) || EHR_EXTRACT_INTERACTION_ID07.equals(interactionId)) {
                ehrExtractMessageHandler.handleMessage(inboundMessage, conversationId);
            } else if (CONTINUE_ATTACHMENT_INTERACTION_ID.equals(interactionId)) {
                continueMessageHandler.handleMessage(inboundMessage, conversationId);
            } else {
                LOGGER.info("Handling message with [{}] interaction id not implemented", interactionId);
            }
            return true;
        } catch (JMSException | JAXBException | SAXException e) {
            LOGGER.error("Unable to read the content of the inbound MHS message", e);

            // Current child try catch blocks do not detect this condition so no failed migration log is added...
            // We are however unlikely to have a payload at this point so cannot send a NACK
            if (conversationId != null && !conversationId.isEmpty()) {
                migrationStatusLogService.addMigrationStatusLog(EHR_GENERAL_PROCESSING_ERROR,
                                                                conversationId,
                                                                null,
                                                                UNEXPECTED_CONDITION.getCode());
            }
            return false;
        } catch (JsonProcessingException | DataFormatException e) {
            LOGGER.error("Unable to parse messages", e);
            return false;
        } catch (InlineAttachmentProcessingException | AttachmentLogException e) {
            LOGGER.error("Unable to process inline attachments", e);
            return false;
        } catch (AttachmentNotFoundException e) {
            LOGGER.error("Unable to find attachment reference inbound message", e);
            return false;
        } catch (BundleMappingException e) {
            LOGGER.error("Unable to map EHR Extract to FHIR bundle", e);
            return false;
        } catch (ParseException e) {
            LOGGER.error("Unable to parse Ebxml References", e);
            return false;
        } catch (TransformerException e) {
            LOGGER.error("Unable to process skeleton section of message", e);
            return false;
        } catch (MhsServerErrorException e) {
            LOGGER.error("Unable to sent message to MHS due to MHS Outbound server error");
            throw e;
        }
    }

    private InboundMessage readMessage(Message message) throws JMSException, JsonProcessingException {
        var body = jmsReader.readMessage(message);
        return objectMapper.readValue(body, InboundMessage.class);
    }

    private void applyConversationId(String conversationId) {
        mdcService.applyConversationId(conversationId);
    }
}
