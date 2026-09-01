package uk.nhs.adaptors.pss.translator.amqp;

import java.util.UUID;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.pss.translator.Gp2gpTranslatorApplication;
import uk.nhs.adaptors.pss.translator.config.MhsQueueProperties;
import uk.nhs.adaptors.pss.translator.exception.ConversationIdNotFoundException;
import uk.nhs.adaptors.pss.translator.task.MhsQueueMessageHandler;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@SpringBootTest(classes = Gp2gpTranslatorApplication.class)
public class MhsQueueConsumerTest {

    private static final int MAX_REDELIVERIES = 3;
    private static final int FIRST_DELIVERY = 1;

    private static final String DELIVERY_COUNT_PROPERTY = "JMSXDeliveryCount";
    @Mock
    private MhsQueueMessageHandler mhsQueueMessageHandler;
    @Mock
    private MhsDlqPublisher mhsDlqPublisher;
    @Mock
    private MhsQueueProperties mhsQueueProperties;
    @Mock
    private Message message;
    @Mock
    private Session session;
    @Mock
    private MDCService mdcService;
    @InjectMocks
    private MhsQueueConsumer mhsQueueConsumer;

    @Test
    @SneakyThrows
    public void When_Receive_WithSuccess_Expect_MessageAcknowledged() {
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(FIRST_DELIVERY);
        when(mhsQueueMessageHandler.handleMessage(message)).thenReturn(true);

        mhsQueueConsumer.receive(message, session);

        verify(message, times(1)).acknowledge();
        verify(mhsDlqPublisher, times(0)).sendToMhsDlq(message);
    }

    @Test
    @SneakyThrows
    public void When_Receive_WithFailure_Expect_MessageSentToDLQ() {
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(FIRST_DELIVERY);
        when(mhsQueueMessageHandler.handleMessage(message)).thenReturn(false);

        mhsQueueConsumer.receive(message, session);

        verify(message, times(0)).acknowledge();
        verify(mhsDlqPublisher, times(1)).sendToMhsDlq(message);
    }

    @Test
    @SneakyThrows
    public void When_ConversationIdNotFound_WithoutDaisyChaining_Expect_SessionRolledBack() {
        when(mhsQueueMessageHandler.handleMessage(message)).thenThrow(ConversationIdNotFoundException.class);
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(MAX_REDELIVERIES + 1);

        mhsQueueConsumer.receive(message, session);

        verify(session, times(1)).rollback();
    }

    @Test
    @SneakyThrows
    public void When_ConversationIdNotFound_WithMaxRedeliveriesReached_Expect_SessionRolledBack() {
        when(mhsQueueMessageHandler.handleMessage(message)).thenThrow(ConversationIdNotFoundException.class);
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(MAX_REDELIVERIES + 1);
        when(mhsQueueProperties.getMaxRedeliveries()).thenReturn(MAX_REDELIVERIES);

        mhsQueueConsumer.receive(message, session);

        verify(session, times(1)).rollback();
    }

    @Test
    @SneakyThrows
    public void When_ConversationIdNotFound_WithMaxRedeliveriesNotReached_Expect_SessionRolledBack() {
        when(mhsQueueMessageHandler.handleMessage(message)).thenThrow(ConversationIdNotFoundException.class);
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(MAX_REDELIVERIES);
        when(mhsQueueProperties.getMaxRedeliveries()).thenReturn(MAX_REDELIVERIES);

        mhsQueueConsumer.receive(message, session);

        verify(session, times(1)).rollback();
    }

    @Test
    @SneakyThrows
    public void When_MessageNotReceived_Retry() {
        doThrow(RuntimeException.class).when(mhsQueueMessageHandler).handleMessage(message);
        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        doNothing().when(mdcService).resetAllMdcKeys();

        MhsQueueConsumer mhsQueueConsumerSpy = spy(mhsQueueConsumer);
        assertThrows(RuntimeException.class, () -> mhsQueueConsumerSpy.receive(message, session));

        verify(mhsQueueConsumerSpy, times(3)).receive(message, session);
    }
}
