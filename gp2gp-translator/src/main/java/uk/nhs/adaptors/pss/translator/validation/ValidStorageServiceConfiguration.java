package uk.nhs.adaptors.pss.translator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Documented
@Constraint(validatedBy = StorageServiceConfigurationValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStorageServiceConfiguration {
    String message() default "Invalid Storage Service Configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}