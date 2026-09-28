package com.bankms.exception;

import com.bankms.dto.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Builds the error body. Shared by the MVC exception handler and by the security filters, which run
 * before any controller and would otherwise emit Spring's default (differently shaped) errors.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorFactory {

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ApiError create(ErrorCode code, String message, HttpServletRequest request,
                           List<ApiError.FieldViolation> fieldErrors) {
        return new ApiError(
                Instant.now(clock),
                code.getStatus().value(),
                code.name(),
                message != null ? message : code.getDefaultMessage(),
                request.getRequestURI(),
                fieldErrors);
    }

    public void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code, String message)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), create(code, message, request, List.of()));
    }
}
