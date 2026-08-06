package uk.nhs.adaptors.pss.translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import uk.nhs.adaptors.pss.translator.amqp.MhsQueueConsumer;

@Configuration
@EnableRetry
public class ConsumerConfiguration {

    @Bean
    public MhsQueueConsumer mhsQueueConsumer()
    {
        return new MhsQueueConsumer();
    }
}
