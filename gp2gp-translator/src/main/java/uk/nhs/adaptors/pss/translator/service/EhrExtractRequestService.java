package uk.nhs.adaptors.pss.translator.service;

import static uk.nhs.adaptors.pss.translator.util.DateFormatUtil.toHl7Format;
import static uk.nhs.adaptors.pss.translator.util.template.TemplateUtil.fillTemplate;
import static uk.nhs.adaptors.pss.translator.util.template.TemplateUtil.loadTemplate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.mustachejava.Mustache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.model.TransferRequestMessage;
import uk.nhs.adaptors.common.util.DateUtils;
import uk.nhs.adaptors.pss.translator.util.template.parameter.SendEhrExtractRequestParams;

@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class EhrExtractRequestService {
    private static final Mustache SEND_EHR_EXTRACT_REQUEST_TEMPLATE =
        loadTemplate("sendEhrExtractRequest.mustache");

    private final DateUtils dateUtils;
    private final IdGeneratorService idGeneratorService;

    public String buildEhrExtractRequest(TransferRequestMessage transferRequestMessage, String messageId) {
        LOGGER.debug("Building EHRExtractRequest");

        SendEhrExtractRequestParams params = SendEhrExtractRequestParams.builder()
            .messageId(messageId)
            .timestamp(toHl7Format(dateUtils.getCurrentInstant()))
            .toAsid(transferRequestMessage.getToAsid())
            .fromAsid(transferRequestMessage.getFromAsid())
            .nhsNumber(transferRequestMessage.getPatientNhsNumber())
            .ehrRequestId(idGeneratorService.generateUuid())
            .fromOds(transferRequestMessage.getFromOds())
            .toOds(transferRequestMessage.getToOds())
            .build();

        return fillTemplate(SEND_EHR_EXTRACT_REQUEST_TEMPLATE, params);
    }
}
