package uk.nhs.adaptors.pss.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import uk.nhs.adaptors.pss.translator.validation.ValidMhsQueueProperties;

@Component
@ConfigurationProperties(prefix = "amqp.mhs")
@Getter
@Setter
@Validated
@ValidMhsQueueProperties
public class MhsQueueProperties {
    private String queueName;
    private String broker;
    private String username;
    private String password;
    private int maxRedeliveries;
    private String dlqPrefix;

    public String getDLQName() {
        return getDlqPrefix() + getQueueName();
    }
}
