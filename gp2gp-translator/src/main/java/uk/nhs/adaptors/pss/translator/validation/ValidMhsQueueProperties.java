package uk.nhs.adaptors.pss.translator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;


@Documented
@Constraint(validatedBy = MhsQueuePropertyValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMhsQueueProperties {
    String message() default "Invalid MHS Queue Configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}