package uk.nhs.adaptors.pss.translator.service;

import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.toHl7Format;
import static uk.nhs.adaptors.pss.translator.util.template.TemplateUtil.fillTemplate;
import static uk.nhs.adaptors.pss.translator.util.template.TemplateUtil.loadTemplate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.mustachejava.Mustache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.pss.translator.model.ACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.util.template.parameter.ApplicationAcknowledgmentParams;

@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class ApplicationAcknowledgementMessageService {
    private static final Mustache NACK_MESSAGE_TEMPLATE =
        loadTemplate("applicationAcknowledgementTemplate.mustache");

    private static final Mustache ACK_MESSAGE_TEMPLATE =
        loadTemplate("applicationAcknowledgementTemplate.mustache");

    private final DateUtils dateUtils;

    public String buildNackMessage(NACKMessageData messageData, String messageId) throws IllegalArgumentException {

        LOGGER.debug("Building NACK message for message = [{}]", messageData.getMessageRef());

        ApplicationAcknowledgmentParams params = ApplicationAcknowledgmentParams.builder()
            .messageId(messageId)
            .creationTime(toHl7Format(dateUtils.getCurrentInstant()))
            .toAsid(messageData.getToAsid())
            .fromAsid(messageData.getFromAsid())
            .messageRef(messageData.getMessageRef())
            .nackCode(messageData.getNackReason().getCode())
            .build();

        return fillTemplate(NACK_MESSAGE_TEMPLATE, params);
    }

    public String buildAckMessage(ACKMessageData messageData, String messageId) throws IllegalArgumentException {

        LOGGER.debug("Building ACK message for message = [{}]", messageData.getMessageRef());

        ApplicationAcknowledgmentParams params = ApplicationAcknowledgmentParams.builder()
            .messageId(messageId)
            .creationTime(toHl7Format(dateUtils.getCurrentInstant()))
            .toAsid(messageData.getToAsid())
            .fromAsid(messageData.getFromAsid())
            .messageRef(messageData.getMessageRef())
            .build();

        return fillTemplate(ACK_MESSAGE_TEMPLATE, params);
    }
}
