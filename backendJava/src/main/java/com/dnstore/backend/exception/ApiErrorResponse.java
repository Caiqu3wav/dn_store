package com.dnstore.backend.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors) {

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, Map.of());
    }

    public static ApiErrorResponse of(HttpStatus status, String message, String path, Map<String, String> errors) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, errors);
    }
}