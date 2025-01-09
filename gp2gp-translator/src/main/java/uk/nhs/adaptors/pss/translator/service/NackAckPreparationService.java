package uk.nhs.adaptors.pss.translator.service;

import static uk.nhs.adaptors.common.enums.MigrationStatus.COPC_ACKNOWLEDGED;
import static uk.nhs.adaptors.common.enums.MigrationStatus.FINAL_ACK_SENT;

import org.hl7.v3.COPCIN000001UK01Message;
import org.hl7.v3.RCMRIN030000UKMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.connector.service.MigrationStatusLogService;
import uk.nhs.adaptors.pss.translator.model.ACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKMessageData;
import uk.nhs.adaptors.pss.translator.model.NACKReason;
import uk.nhs.adaptors.pss.translator.task.SendACKMessageHandler;
import uk.nhs.adaptors.pss.translator.task.SendNACKMessageHandler;
import uk.nhs.adaptors.pss.translator.util.XmlParseUtilService;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class NackAckPreparationService implements NackAckPrepInterface {

    private final SendNACKMessageHandler sendNACKMessageHandler;
    private final SendACKMessageHandler sendACKMessageHandler;
    private final MigrationStatusLogService migrationStatusLogService;

    public boolean sendAckMessage(RCMRIN030000UKMessage payload, String conversationId) {

        LOGGER.debug("Sending Final ACK message for Conversation ID: [{}]", conversationId);

        migrationStatusLogService.
                addMigrationStatusLog(FINAL_ACK_SENT, conversationId, null, null);

        return sendACKMessageHandler.prepareAndSendMessage(prepareAckMessageData(
                payload,
                conversationId
        ));
    }

    public boolean sendAckMessage(COPCIN000001UK01Message payload, String conversationId, String losingPracticeOdsCode) {

        LOGGER.debug("Sending ACK message for COPC message with Conversation ID: [{}]", conversationId);

        migrationStatusLogService.
                addMigrationStatusLog(COPC_ACKNOWLEDGED, conversationId, null, null);

        return sendACKMessageHandler.prepareAndSendMessage(prepareAckMessageData(
                payload,
                conversationId,
                losingPracticeOdsCode
        ));
    }

    private ACKMessageData prepareAckMessageData(RCMRIN030000UKMessage payload,
                                                 String conversationId) {

        String toOdsCode = XmlParseUtilService.parseToOdsCode(payload);
        String messageRef = XmlParseUtilService.parseMessageRef(payload);
        String toAsid = XmlParseUtilService.parseToAsid(payload);
        String fromAsid = XmlParseUtilService.parseFromAsid(payload);

        return ACKMessageData.builder()
                .conversationId(conversationId)
                .toOdsCode(toOdsCode)
                .messageRef(messageRef)
                .toAsid(toAsid)
                .fromAsid(fromAsid)
                .build();
    }

    private ACKMessageData prepareAckMessageData(COPCIN000001UK01Message payload,
                                                String conversationId, String losingPracticeOdsCode) {

        String messageRef = XmlParseUtilService.parseMessageRef(payload);
        String toAsid = XmlParseUtilService.parseToAsid(payload);
        String fromAsid = XmlParseUtilService.parseFromAsid(payload);

        return ACKMessageData.builder()
                .conversationId(conversationId)
                .toOdsCode(losingPracticeOdsCode)
                .messageRef(messageRef)
                .toAsid(toAsid)
                .fromAsid(fromAsid)
                .build();
    }


    public NACKMessageData prepareNackMessageData(NACKReason reason, RCMRIN030000UKMessage payload,
                                                  String conversationId) {

        String toOdsCode = XmlParseUtilService.parseToOdsCode(payload);
        String messageRef = XmlParseUtilService.parseMessageRef(payload);
        String toAsid = XmlParseUtilService.parseToAsid(payload);
        String fromAsid = XmlParseUtilService.parseFromAsid(payload);

        return NACKMessageData.builder()
                .conversationId(conversationId)
                .nackReason(reason)
                .toOdsCode(toOdsCode)
                .messageRef(messageRef)
                .toAsid(toAsid)
                .fromAsid(fromAsid)
                .build();
    }

    public NACKMessageData prepareNackMessageData(NACKReason reason, COPCIN000001UK01Message payload,
                                                   String conversationId) {

        String toOdsCode = XmlParseUtilService.parseToOdsCode(payload);
        String messageRef = XmlParseUtilService.parseMessageRef(payload);
        String toAsid = XmlParseUtilService.parseToAsid(payload);
        String fromAsid = XmlParseUtilService.parseFromAsid(payload);

        return NACKMessageData.builder()
                .conversationId(conversationId)
                .nackReason(reason)
                .toOdsCode(toOdsCode)
                .messageRef(messageRef)
                .toAsid(toAsid)
                .fromAsid(fromAsid)
                .build();
    }

    public boolean sendNackMessage(NACKReason reason, RCMRIN030000UKMessage payload, String conversationId) {

        LOGGER.debug("Sending NACK message with acknowledgement code [{}] for message EHR Extract message [{}]", reason.getCode(),
                payload.getId().getRoot());

        migrationStatusLogService.addMigrationStatusLog(reason.getMigrationStatus(), conversationId, null, reason.getCode());

        return sendNACKMessageHandler.prepareAndSendMessage(prepareNackMessageData(
                reason,
                payload,
                conversationId
        ));
    }

    public boolean sendNackMessage(NACKReason reason, COPCIN000001UK01Message payload, String conversationId) {

        LOGGER.debug("Sending NACK message with acknowledgement code [{}] for message COPC message [{}]", reason.getCode(),
                payload.getId().getRoot());

        migrationStatusLogService.addMigrationStatusLog(reason.getMigrationStatus(), conversationId, null, reason.getCode());

        return sendNACKMessageHandler.prepareAndSendMessage(prepareNackMessageData(
                reason,
                payload,
                conversationId
        ));
    }

}
