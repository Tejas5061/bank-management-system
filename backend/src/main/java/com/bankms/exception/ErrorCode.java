package com.bankms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. Clients branch on {@code errorCode}, never on the message,
 * so messages can be reworded freely.
 */
@Getter
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "An Idempotency-Key header is required for this operation"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "The OTP is invalid or has expired"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The access token has expired"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "The access token is invalid"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The session has expired, please sign in again"),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This user account has been disabled"),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),

    // 405 / 415
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported"),

    // 409
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "A resource with the same identity already exists"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was modified by someone else, please retry"),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this Idempotency-Key is already being processed"),

    // 422 - request understood, but a banking rule forbids it
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "This Idempotency-Key was already used for a different request"),
    KYC_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_ENTITY, "KYC must be verified before the account can transact"),
    ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "The account is frozen"),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "The account is closed"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Daily transfer limit exceeded"),
    BENEFICIARY_COOLING_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY, "The beneficiary is still in its cooling period"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "The operation is not allowed"),

    // 423
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts; the account is temporarily locked"),

    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
