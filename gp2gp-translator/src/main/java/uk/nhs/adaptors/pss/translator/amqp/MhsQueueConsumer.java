package uk.nhs.adaptors.pss.translator.amqp;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.pss.translator.config.MhsQueueProperties;
import uk.nhs.adaptors.pss.translator.exception.ConversationIdNotFoundException;
import uk.nhs.adaptors.pss.translator.task.MhsQueueMessageHandler;

@Component
@ConditionalOnProperty(value = "amqp.daisyChaining", havingValue = "false")
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class MhsQueueConsumer {
    private final MhsQueueMessageHandler mhsQueueMessageHandler;
    private final MhsDlqPublisher mhsDlqPublisher;
    private final MhsQueueProperties mhsQueueProperties;
    private final MDCService mdcService;

    @JmsListener(destination = "${amqp.mhs.queueName}", containerFactory = "mhsQueueJmsListenerFactory")
    @SneakyThrows
    public void receive(Message message, Session session) {
        String messageId = message.getJMSMessageID();
        int deliveryCount = message.getIntProperty("JMSXDeliveryCount");
        LOGGER.debug("Received a message from MHSQueue, message_id=[{}], delivery_count=[{}]", messageId, deliveryCount);

        try {

            if (mhsQueueMessageHandler.handleMessage(message)) {
                message.acknowledge();
                LOGGER.debug("Acknowledged MHSQueue message_id=[{}]", messageId);
            } else {
                LOGGER.info("Sending message_id=[{}] to the dead letter queue", messageId);
                mhsDlqPublisher.sendToMhsDlq(message);
            }
        } catch (ConversationIdNotFoundException e) {
            if (deliveryCount > mhsQueueProperties.getMaxRedeliveries()) {
                LOGGER.info("Conversation ID [{}] not recognised. Sending message to Dead Letter Queue", e.getConversationId());
            }

            LOGGER.debug("Rolling back session for message_id=[{}], unrecognised conversation ID", messageId);
            session.rollback();
        } finally {
            mdcService.resetAllMdcKeys();
        }
    }
}
