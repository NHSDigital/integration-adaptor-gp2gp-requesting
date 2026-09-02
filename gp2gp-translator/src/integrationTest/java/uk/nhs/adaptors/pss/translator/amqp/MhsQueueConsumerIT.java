package uk.nhs.adaptors.pss.translator.amqp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import uk.nhs.adaptors.common.service.MDCService;
import uk.nhs.adaptors.pss.translator.Gp2gpTranslatorApplication;
import uk.nhs.adaptors.pss.translator.task.MhsQueueMessageHandler;

@SpringBootTest(classes = Gp2gpTranslatorApplication.class)
@TestPropertySource(properties = "amqp.daisyChaining=false")
public class MhsQueueConsumerIT {

    private static final String DELIVERY_COUNT_PROPERTY = "JMSXDeliveryCount";

    @Autowired
    private MhsQueueConsumer mhsQueueConsumer;

    @MockitoSpyBean
    private MhsQueueMessageHandler mhsQueueMessageHandler;

    @MockitoBean
    private MhsDlqPublisher mhsDlqPublisher;

    @MockitoBean
    private MDCService mdcService;

    @Test
    public void When_ReceiveThrowsRuntimeException_Expect_ReceiveMethodRetriedThreeTimes() throws Exception {
        Message message = mock(Message.class);
        Session session = mock(Session.class);

        when(message.getJMSMessageID()).thenReturn(UUID.randomUUID().toString());
        when(message.getIntProperty(DELIVERY_COUNT_PROPERTY)).thenReturn(1);
        doThrow(new RuntimeException("Test failure"))
            .when(mhsQueueMessageHandler).handleMessage(message);

        assertThrows(RuntimeException.class, () -> mhsQueueConsumer.receive(message, session));

        verify(mhsQueueMessageHandler, times(3)).handleMessage(message);
        verifyNoInteractions(mhsDlqPublisher);
        verify(mdcService, times(3)).resetAllMdcKeys();
    }
}
