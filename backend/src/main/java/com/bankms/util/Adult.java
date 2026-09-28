package com.bankms.util;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.LocalDate;

/** The date of birth must make the person at least {@link #minAge()} years old today. */
@Documented
@Constraint(validatedBy = Adult.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Adult {

    String message() default "must be at least {minAge} years old";

    int minAge() default 18;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Spring injects the application Clock, so "today" is the bank's business date. */
    class Validator implements ConstraintValidator<Adult, LocalDate> {

        private final Clock clock;
        private int minAge;

        public Validator(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void initialize(Adult annotation) {
            this.minAge = annotation.minAge();
        }

        @Override
        public boolean isValid(LocalDate dateOfBirth, ConstraintValidatorContext context) {
            return dateOfBirth == null || !dateOfBirth.plusYears(minAge).isAfter(LocalDate.now(clock));
        }
    }
}
