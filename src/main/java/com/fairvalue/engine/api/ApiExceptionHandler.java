package com.fairvalue.engine.api;

import com.fairvalue.engine.api.dto.ApiEnvelope;
import com.fairvalue.engine.config.ApiPlatformFilter;
import com.fairvalue.engine.config.ApiPlatformProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final ApiPlatformProperties properties;

    public ApiExceptionHandler(ApiPlatformProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "illegal_state", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request body.");
        return build(HttpStatus.BAD_REQUEST, "validation_error", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", ex.getMessage(), request);
    }

    private ResponseEntity<?> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        if (!shouldWrap(request)) {
            return ResponseEntity.status(status).body(legacyBody(status, code, message));
        }
        String requestId = (String) request.getAttribute(ApiPlatformFilter.REQUEST_ID_ATTR);
        Instant timestamp = (Instant) request.getAttribute(ApiPlatformFilter.REQUEST_TIMESTAMP_ATTR);
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        return ResponseEntity.status(status).body(ApiEnvelope.error(requestId, timestamp, code, message == null ? code : message));
    }

    private boolean shouldWrap(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/v1/")) {
            return false;
        }
        return properties.shouldWrap(request.getHeader(properties.getEnvelopeHeader()));
    }

    private Map<String, Object> legacyBody(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message == null ? code : message);
        body.put("success", false);
        return body;
    }
}
