package uk.nhs.adaptors.pss.translator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.adaptors.pss.translator.storage.StorageServiceConfiguration;

import java.util.ArrayList;
import java.util.TreeMap;

@Slf4j
public class StorageServiceConfigurationValidator
        implements ConstraintValidator<ValidStorageServiceConfiguration, StorageServiceConfiguration> {

    private static final String OMIT_ENV_VARIABLE_MESSAGE = "Env variable not required when using storage type %s: %s";
    private static final String MISSING_ENV_VARIABLE_MESSAGE = "Env variable not provided: %s";

    enum Rule {
        OPTIONAL,
        REQUIRED,
        OMIT
    }

    private static final String STORAGE_TYPE = "STORAGE_TYPE";
    private static final String STORAGE_REGION = "STORAGE_REGION";
    private static final String STORAGE_CONTAINER_NAME = "STORAGE_CONTAINER_NAME";
    private static final String STORAGE_REFERENCE = "STORAGE_REFERENCE";
    private static final String STORAGE_SECRET = "STORAGE_SECRET";

    @Override
    public boolean isValid(StorageServiceConfiguration config, ConstraintValidatorContext context) {
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

    private TreeMap<String, String> getEnvironmentVariables(StorageServiceConfiguration configuration) {
        TreeMap<String, String> environmentVariables = new TreeMap<>();

        environmentVariables.put(STORAGE_TYPE, configuration.getType());
        environmentVariables.put(STORAGE_REGION, configuration.getRegion());
        environmentVariables.put(STORAGE_CONTAINER_NAME, configuration.getContainerName());
        environmentVariables.put(STORAGE_REFERENCE, configuration.getAccountReference());
        environmentVariables.put(STORAGE_SECRET, configuration.getAccountSecret());

        return environmentVariables;
    }

    private ArrayList<String> validateAgainstRuleset(TreeMap<String, String> environmentVariables) {
        ArrayList<String> messages = new ArrayList<>();

        ArrayList<ValidationRule> ruleset = this.getRuleset(environmentVariables.get(STORAGE_TYPE));

        for (var config : ruleset) {
            String configValue = environmentVariables.get(config.getName());
            if (config.getRule() == Rule.OMIT && !configValue.isBlank()) {
                messages.add(String.format(OMIT_ENV_VARIABLE_MESSAGE, environmentVariables.get(STORAGE_TYPE), config.getName()));
                continue;
            }
            if (config.getRule() == Rule.REQUIRED && configValue.isBlank()) {
                messages.add(String.format(MISSING_ENV_VARIABLE_MESSAGE, config.getName()));
            }
        }

        return messages;
    }

    private ArrayList<ValidationRule> getRuleset(String storageType) {
        ArrayList<ValidationRule> ruleset = new ArrayList<>();
        ruleset.add(new ValidationRule(STORAGE_TYPE, Rule.REQUIRED));

        if (storageType.equalsIgnoreCase("S3")) {
            ruleset.add(new ValidationRule(STORAGE_REGION, Rule.REQUIRED));
            ruleset.add(new ValidationRule(STORAGE_CONTAINER_NAME, Rule.REQUIRED));
//            ruleset.add(new ValidationRule(STORAGE_REFERENCE, Rule.OMIT));
//            ruleset.add(new ValidationRule(STORAGE_SECRET, Rule.OMIT));
        }
        if (storageType.equalsIgnoreCase("Azure")) {
            ruleset.add(new ValidationRule(STORAGE_REGION, Rule.OMIT));
            ruleset.add(new ValidationRule(STORAGE_CONTAINER_NAME, Rule.REQUIRED));
            ruleset.add(new ValidationRule(STORAGE_REFERENCE, Rule.REQUIRED));
            ruleset.add(new ValidationRule(STORAGE_SECRET, Rule.REQUIRED));
        }
        return ruleset;
    }

    private static void setConstraintViolation(ConstraintValidatorContext context, String message) {
        LOGGER.error(message);
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

    @Getter
    @Setter
    class ValidationRule {
        private String name;
        private Rule rule;

        ValidationRule(String configuration, Rule rule) {
            this.name = configuration;
            this.rule = rule;
        }
    }

}
