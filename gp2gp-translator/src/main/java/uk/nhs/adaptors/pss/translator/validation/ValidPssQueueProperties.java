package uk.nhs.adaptors.pss.translator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Documented
@Constraint(validatedBy = PssQueuePropertyValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPssQueueProperties {
    String message() default "Invalid PSS Queue Configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}