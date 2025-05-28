package uk.nhs.adaptors.pss.translator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "timeout")
public class TimeoutProperties {
    private int ehrExtractWeighting;
    private int copcWeighting;
    private int sdsPollFrequency;
    private boolean migrationTimeoutOverride;
}
