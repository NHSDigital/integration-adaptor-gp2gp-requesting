package uk.nhs.adaptors.pss.translator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import uk.nhs.adaptors.pss.translator.config.MhsQueueProperties;

import java.util.ArrayList;
import java.util.TreeMap;

@Slf4j
public class MhsQueuePropertyValidator
        implements ConstraintValidator<ValidMhsQueueProperties, MhsQueueProperties> {
    private static final String MISSING_ENV_VARIABLE_MESSAGE = "Env variable not provided: %s";

    @Override
    public boolean isValid(MhsQueueProperties config, ConstraintValidatorContext context) {
        TreeMap<String, String> environmentVariables = getEnvironmentVariables(config);

        ArrayList<String> validationMessages = validateAgainstRuleset(environmentVariables);

        if (!validationMessages.isEmpty()) {
            for (var message : validationMessages) {
                setConstraintViolation(context, message);
            }
            return false;
        }
        return true;
    }

    private TreeMap<String, String> getEnvironmentVariables(MhsQueueProperties configuration) {
        TreeMap<String, String> environmentVariables =  new TreeMap<>();

        environmentVariables.put("MHS_AMQP_BROKER", configuration.getBroker());
        environmentVariables.put("MHS_AMQP_USERNAME", configuration.getUsername());
        environmentVariables.put("MHS_AMQP_PASSWORD", configuration.getPassword());

        return environmentVariables;
    }

    private ArrayList<String> validateAgainstRuleset(TreeMap<String, String> environmentVariables) {
        ArrayList<String> messages = new ArrayList<>();

        for (var variable : environmentVariables.entrySet()) {
            if (StringUtils.isBlank(variable.getValue())) {
                messages.add(String.format(MISSING_ENV_VARIABLE_MESSAGE, variable.getKey()));
            }
        }

        return messages;
    }

    private static void setConstraintViolation(ConstraintValidatorContext context, String message) {
        LOGGER.error(message);
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

}
