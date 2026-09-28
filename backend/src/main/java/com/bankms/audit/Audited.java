package com.bankms.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as a sensitive action. {@link AuditAspect} records who did it, when, from
 * where, and whether it succeeded, without the method containing any audit code.
 * <p>
 * {@code entityId}, {@code details} and {@code actor} are SpEL expressions evaluated against the
 * method arguments (by name, e.g. {@code #request.amount}) and, on success, {@code #result}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    String entityType() default "";

    String entityId() default "";

    String details() default "";

    /** Identifies the actor when nobody is authenticated yet (e.g. login): usually the email argument. */
    String actor() default "";
}
