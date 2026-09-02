package uk.nhs.adaptors.pss.translator.amqp;

import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.pss.translator.task.QueueMessageHandler;

@Component
@Slf4j
@EnableRetry
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PssQueueConsumer {

    private final QueueMessageHandler queueMessageHandler;
    private final MDCService mdcService;

    @Retryable
    @JmsListener(destination = "${amqp.pss.queueName}", containerFactory = "pssQueueJmsListenerFactory")
    @SneakyThrows
    public void receive(Message message, Session session) {
        String messageId = message.getJMSMessageID();
        int deliveryCount = message.getIntProperty("JMSXDeliveryCount");
        LOGGER.debug("Received a message from PSSQueue, message_id=[{}], body=[{}], delivery_count=[{}]",
            messageId, ((TextMessage) message).getText(), deliveryCount);

        try {
            if (queueMessageHandler.handle(message)) {
                message.acknowledge();
                LOGGER.debug("Acknowledged PSSQueue message_id=[{}]", messageId);
            } else {
                LOGGER.debug("Rolling back session for message_id=[{}]", messageId);
                session.rollback();
            }
        } finally {
            mdcService.resetAllMdcKeys();
        }
    }
}
