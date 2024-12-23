package uk.nhs.adaptors.pss.translator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.v3.RCMRIN030000UKMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.nhs.adaptors.common.enums.ConfirmationResponse;
import uk.nhs.adaptors.common.model.AcknowledgeRecordMessage;
import uk.nhs.adaptors.pss.translator.mhs.model.InboundMessage;
import uk.nhs.adaptors.pss.translator.model.NACKReason;

import jakarta.xml.bind.JAXBException;
import java.util.Map;

import static uk.nhs.adaptors.common.enums.ConfirmationResponse.ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.ACCEPTED;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.FAILED_TO_INTEGRATE;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.NON_ABA_INCORRECT_PATIENT;
import static uk.nhs.adaptors.common.enums.ConfirmationResponse.SUPPRESSED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.ABA_EHR_EXTRACT_SUPPRESSED;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.CLINICAL_SYSTEM_INTEGRATION_FAILURE;
import static uk.nhs.adaptors.pss.translator.model.NACKReason.NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT;
import static uk.nhs.adaptors.pss.translator.util.XmlUnmarshallUtil.unmarshallString;

@Slf4j
@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class AcknowledgeRecordService {

    private final NackAckPrepInterface nackAckPrepInterface;
    private final ObjectMapper objectMapper;

    private static final Map<ConfirmationResponse, NACKReason> REASONS = Map.of(
            ABA_INCORRECT_PATIENT, ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT,
            NON_ABA_INCORRECT_PATIENT, NON_ABA_EHR_EXTRACT_REJECTED_WRONG_PATIENT,
            FAILED_TO_INTEGRATE, CLINICAL_SYSTEM_INTEGRATION_FAILURE,
            SUPPRESSED, ABA_EHR_EXTRACT_SUPPRESSED
    );

    public boolean prepareAndSendAcknowledgementMessage(AcknowledgeRecordMessage acknowledgeRecordMessage) {
        RCMRIN030000UKMessage message;

        try {
            message = parseOriginalMessage(acknowledgeRecordMessage);
        } catch (Exception exception) {
            LOGGER.error("Original message was not parsed due to an exception: {}", exception.getMessage());
            return false;
        }

        var conversationId = acknowledgeRecordMessage.getConversationId();
        var confirmationResponse = acknowledgeRecordMessage.getConfirmationResponse();

        if (StringUtils.isBlank(conversationId) || confirmationResponse == null) {
            return false;
        }

        if (confirmationResponse == ACCEPTED) {
            return nackAckPrepInterface.sendAckMessage(message, conversationId);
        }

        var nackReason = REASONS.get(confirmationResponse);
        return nackAckPrepInterface.sendNackMessage(nackReason, message, conversationId);
    }

    private RCMRIN030000UKMessage parseOriginalMessage(AcknowledgeRecordMessage message) throws JAXBException, JsonProcessingException {

        var payload = objectMapper.readValue(message.getOriginalMessage(), InboundMessage.class).getPayload();

        return unmarshallString(payload, RCMRIN030000UKMessage.class);
    }

}
