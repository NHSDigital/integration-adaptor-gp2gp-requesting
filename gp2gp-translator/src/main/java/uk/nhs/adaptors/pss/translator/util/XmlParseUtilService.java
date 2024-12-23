package uk.nhs.adaptors.pss.translator.util;

import java.io.StringWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hl7.v3.COPCIN000001UK01Message;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.EbxmlReference;
import uk.nhs.adaptors.pss.translator.service.XPathService;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class XmlParseUtilService {

    private final XPathService xPathService;

    private static final String FILENAME_PATTERN = "Filename=\"([\\S]{1}[^\"]*)\"";

    private static final String COMPRESSED_PATTERN = "Compressed=(Yes|No)";

    public static boolean parseOriginalBase64(String description) throws ParseException {
        Pattern pattern = Pattern.compile("OriginalBase64=(Yes|No)");
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1).equals("Yes");
        }

        throw new ParseException("Unable to parse isBase64", 0);
    }

    public static boolean parseLargeAttachment(String description) throws ParseException {
        Pattern pattern = Pattern.compile("LargeAttachment=(Yes|No)");
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1).equals("Yes");
        }

        throw new ParseException("Unable to parse isLargeAttachment", 0);
    }

    public static boolean parseCompressed(String description) throws ParseException {
        Pattern pattern = Pattern.compile(COMPRESSED_PATTERN);
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1).equals("Yes");
        }

        throw new ParseException("Unable to parse isCompressed field in description", 0);
    }

    public static String parseContentType(String description) throws ParseException {
        Pattern pattern = Pattern.compile("ContentType=([A-Za-z\\d\\-/.]*)");
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new ParseException("Unable to parse ContentType", 0);
    }

    public static String parseNhsNumber(RCMRIN030000UKMessage payload) {
        return payload.getControlActEvent()
            .getSubject()
            .getEhrExtract()
            .getRecordTarget()
            .getPatient()
            .getId()
            .getExtension();
    }

    public static String parseFilename(String description) throws ParseException {
        Pattern pattern = Pattern.compile(FILENAME_PATTERN);
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new ParseException("Unable to parse originalFilename field in description", 0);
    }

    public static Boolean isDescriptionEmisStyle(String description) {
        Pattern pattern = Pattern.compile(FILENAME_PATTERN);
        Matcher matcher = pattern.matcher(description);
        boolean hasFilename = matcher.find();
        pattern = Pattern.compile(COMPRESSED_PATTERN);
        matcher = pattern.matcher(description);
        boolean hasCompressed = matcher.find();

        if (!hasFilename && !hasCompressed) {
            return true;
        }

        return false;
    }

    public static int parseFileLength(String description) {
        Pattern pattern = Pattern.compile("Length=([\\d]*)");
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static boolean parseIsSkeleton(String description) {
        final String EB_SKELETON_PROP = "X-GP2GP-Skeleton:Yes".toLowerCase();
        return description.replaceAll("\\s+", "").toLowerCase().contains(EB_SKELETON_PROP);
    }

    public static String parseFromAsid(COPCIN000001UK01Message payload) {
        return payload.getCommunicationFunctionRcv()
                .getFirst()
                .getDevice()
                .getId()
                .getFirst()
                .getExtension();
    }

    public static String parseToAsid(COPCIN000001UK01Message payload) {
        return payload.getCommunicationFunctionSnd()
                .getDevice()
                .getId()
                .getFirst()
                .getExtension();
    }

    public static String parseMessageRef(COPCIN000001UK01Message payload) {
        return payload.getId().getRoot();
    }

    public static String parseToOdsCode(COPCIN000001UK01Message payload) {

        Element gp2gpElement = payload.getControlActEvent()
                .getSubject()
                .getPayloadInformation()
                .getValue()
                .getAny()
                .getFirst();

        return getFromPracticeValue(gp2gpElement);
    }


    public static String getFromPracticeValue(Element gp2gpElement) {
        for (int i = 0; i < gp2gpElement.getChildNodes().getLength(); i++) {
            Node currNode = gp2gpElement.getChildNodes().item(i);
            if (currNode.getLocalName().equals("From")) {
                return currNode.getFirstChild().getNodeValue();
            }
        }
        return null;
    }

    public static String parseFromAsid(RCMRIN030000UKMessage payload) {
        return payload.getCommunicationFunctionRcv()
            .getFirst()
            .getDevice()
            .getId()
            .getFirst()
            .getExtension();
    }

    public static String parseToAsid(RCMRIN030000UKMessage payload) {
        return payload.getCommunicationFunctionSnd()
                .getDevice()
                .getId()
                .getFirst()
                .getExtension();
    }

    public static String parseToOdsCode(RCMRIN030000UKMessage payload) {
        return payload.getControlActEvent()
                .getSubject()
                .getEhrExtract()
                .getAuthor()
                .getAgentOrgSDS()
                .getAgentOrganizationSDS()
                .getId()
                .getExtension();
    }

    public static String parseMessageRef(RCMRIN030000UKMessage payload) {
        return payload.getId().getRoot();
    }

    public static String parseFragmentFilename(String description) {
        try {
            return parseFilename(description);
        } catch (ParseException e) {
            return description;
        }
    }

    public List<EbxmlReference> getEbxmlAttachmentsData(InboundMessage inboundMessage) throws SAXException {
        List<EbxmlReference> ebxmlAttachmentsIds = new ArrayList<>();
        final String REFERENCES_ATTACHMENTS_PATH = "/Envelope/Body/Manifest/Reference";
        Document ebXmlDocument = xPathService.parseDocumentFromXml(inboundMessage.getEbXML());
        NodeList referencesAttachment = xPathService.getNodes(ebXmlDocument, REFERENCES_ATTACHMENTS_PATH);

        for (int index = 0; index < referencesAttachment.getLength(); index++) {

            Node referenceNode = referencesAttachment.item(index); //Reference

            if (referenceNode.getNodeType() == Node.ELEMENT_NODE) {

                Element referenceElement = (Element) referenceNode; //description

                String description = referenceElement.getTextContent();
                String hrefAttribute = referenceElement.getAttribute("xlink:href");
                String documentId = referenceElement.getAttribute("eb:id");

                ebxmlAttachmentsIds.add(new EbxmlReference(description, hrefAttribute, documentId));
            }
        }

        return ebxmlAttachmentsIds;
    }

    public String getStringFromDocument(Document doc) throws TransformerException {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(domSource, result);
        return writer.toString();
    }
}
