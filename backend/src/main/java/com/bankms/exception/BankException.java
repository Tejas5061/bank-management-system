package com.bankms.exception;

import lombok.Getter;

/** Base for every expected, client-facing failure. Mapped to the error JSON by GlobalExceptionHandler. */
@Getter
public class BankException extends RuntimeException {

    private final ErrorCode errorCode;

    public BankException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BankException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BankException notFound(String resource, Object id) {
        return new BankException(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + id);
    }

    public static BankException notFound(String resource) {
        return new BankException(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found");
    }

    public static BankException duplicate(String message) {
        return new BankException(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public static BankException rule(String message) {
        return new BankException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
