package com.bankms.exception;

import com.bankms.dto.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns every exception into the same JSON shape: timestamp, status, errorCode, message, path
 * (plus fieldErrors for validation failures). Internal details never reach the client.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiErrorFactory errors;

    @ExceptionHandler(BankException.class)
    ResponseEntity<ApiError> handleBank(BankException ex, HttpServletRequest request) {
        if (ex.getErrorCode().getStatus().is5xxServerError()) {
            log.error("Server-side banking error on {}", request.getRequestURI(), ex);
        }
        return respond(ex.getErrorCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> violations.add(new ApiError.FieldViolation(fe.getField(), messageOf(fe))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> violations.add(new ApiError.FieldViolation(ge.getObjectName(), ge.getDefaultMessage())));
        return respond(ErrorCode.VALIDATION_FAILED, null, request, violations);
    }

    /**
     * Raised instead of MethodArgumentNotValidException when a handler also has constrained
     * parameters (e.g. the Idempotency-Key header), so body errors arrive here too. Report body
     * errors by field name and header/param errors by the name the client actually sent.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors()
                        .forEach(fe -> violations.add(new ApiError.FieldViolation(fe.getField(), messageOf(fe))));
                errors.getGlobalErrors()
                        .forEach(ge -> violations.add(new ApiError.FieldViolation(ge.getObjectName(), ge.getDefaultMessage())));
            } else {
                String name = clientName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error ->
                        violations.add(new ApiError.FieldViolation(name, error.getDefaultMessage())));
            }
        });
        return respond(ErrorCode.VALIDATION_FAILED, null, request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(leaf(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, null, request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, "Malformed JSON or an invalid value in the request body", request, List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return respond(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, null, request, List.of());
        }
        return respond(ErrorCode.VALIDATION_FAILED, "Missing required header '" + ex.getHeaderName() + "'", request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Missing required parameter '" + ex.getParameterName() + "'", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Parameter '" + ex.getName() + "' has an invalid value", request, List.of());
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ApiError> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Cannot sort by '" + ex.getPropertyName() + "'", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, null, request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION, null, request, List.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ApiError> handlePessimisticLock(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Lock acquisition failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, "The account is busy with another operation, please retry", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DUPLICATE_RESOURCE, "The request conflicts with existing data", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, null, request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, null, request, List.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest request,
                                             List<ApiError.FieldViolation> violations) {
        return ResponseEntity.status(code.getStatus()).body(errors.create(code, message, request, violations));
    }

    private static String messageOf(FieldError fe) {
        return fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "is invalid";
    }

    private static String clientName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && StringUtils.hasText(header.value())) {
            return header.value();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && StringUtils.hasText(param.value())) {
            return param.value();
        }
        return parameter.getParameterName();
    }

    private static String leaf(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
