package uk.nhs.adaptors.pss.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import uk.nhs.adaptors.pss.translator.validation.ValidPssQueueProperties;

@Component
@ConfigurationProperties(prefix = "amqp.pss")
@Getter
@Setter
@Validated
@ValidPssQueueProperties
public class PssQueueProperties {
    private String queueName;
    private String broker;
    private String username;
    private String password;
    private int maxRedeliveries;
}
