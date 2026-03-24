package uk.nhs.adaptors.pss.translator.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.transform.TransformerException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.connector.model.PatientAttachmentLog;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.EbxmlReference;
import uk.nhs.adaptors.pss.translator.util.XmlParseUtilService;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SkeletonProcessingService {

    private static final int XML_CONCAT_CONSTANT_LENGTH = 100;

    private final AttachmentHandlerService attachmentHandlerService;
    private final XmlParseUtilService xmlParseUtilService;
    private final XPathService xPathService;


    public InboundMessage updateInboundMessageWithSkeleton(PatientAttachmentLog skeletonLog,
        InboundMessage inboundMessage, String conversationId)
        throws SAXException, TransformerException {

        // merge skeleton message into original payload
        var skeletonAttachment = attachmentHandlerService.getAttachment(
            skeletonLog.getFilename(), conversationId);
        var skeletonFileAsString = new String(skeletonAttachment, StandardCharsets.UTF_8);

        try {
            // if the skeleton starts with the RCMR tag, then we are replacing the whole message.
            // this behaviour is not a part of the specification but we have found this format in some messages
            var replaceEntirePayload = skeletonFileAsString
                .substring(0, XML_CONCAT_CONSTANT_LENGTH).contains("<RCMR_IN030000UK06");
            var skeletonExtractDocument = xPathService.parseDocumentFromXml(skeletonFileAsString);

            if (replaceEntirePayload) {
                // replace the entire inbound message payload
                inboundMessage.setPayload(xmlParseUtilService.getStringFromDocument(skeletonExtractDocument));
            } else {
                // get ebxml references to find document id from skeleton message
                inboundMessage = insertSkeletonIntoInboundMessagePayload(skeletonLog,
                    inboundMessage, skeletonExtractDocument);
            }
            return inboundMessage;

        } catch (RuntimeException runException) {
            throw runException;
        } catch (Exception ex) {
            throw new TransformerException("Skeleton message could not be processed into the original inbound message");
        }
    }

    private InboundMessage insertSkeletonIntoInboundMessagePayload(PatientAttachmentLog skeletonLog,
        InboundMessage inboundMessage, Document skeletonExtractDocument)
        throws SAXException, TransformerException {

        List<EbxmlReference> attachmentReferenceDescription = xmlParseUtilService.getEbxmlAttachmentsData(inboundMessage);
        var ebxmlSkeletonReference = attachmentReferenceDescription
            .stream()
            .filter(reference -> reference.getHref().contains(skeletonLog.getMid()))
            .findFirst();

        if (ebxmlSkeletonReference.isEmpty()) {
            throw new IllegalArgumentException("inboundMessage does not contain a skeleton attachment reference");
        }

        var skeletonDocumentId = ebxmlSkeletonReference.get().getDocumentId();
        var payloadXml = xPathService.parseDocumentFromXml(inboundMessage.getPayload());
        var valueNodes = xPathService.getNodes(payloadXml, "//*/@*[.='" + skeletonDocumentId + "']/parent::*/parent::*");
        var payloadNodeToReplace = valueNodes.item(0);

        if (payloadNodeToReplace == null) {
            throw new IllegalArgumentException(
                    "Could not find payload node matching skeleton document ID: " + skeletonDocumentId
            );
        }
        var payloadNodeToReplaceParent = payloadNodeToReplace.getParentNode();

        var skeletonExtractNodes = skeletonExtractDocument.getElementsByTagName("*");
        var primarySkeletonNode = skeletonExtractNodes.item(0);

        // using xPathServices breaks the xml document pointer, reset it
        payloadXml = payloadNodeToReplaceParent.getOwnerDocument();
        var importedToPayloadNode = payloadXml.importNode(primarySkeletonNode, true);
        payloadNodeToReplaceParent.replaceChild(importedToPayloadNode, payloadNodeToReplace);
        inboundMessage.setPayload(xmlParseUtilService.getStringFromDocument(payloadXml));

        return inboundMessage;
    }
}
