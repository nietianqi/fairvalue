package com.fairvalue.engine.api.dto;

import java.time.Instant;

public record ApiEnvelope<T>(
        boolean success,
        String requestId,
        Instant timestamp,
        T data,
        ApiError error
) {
    public static <T> ApiEnvelope<T> success(String requestId, Instant timestamp, T data) {
        return new ApiEnvelope<>(true, requestId, timestamp, data, null);
    }

    public static ApiEnvelope<Void> error(String requestId, Instant timestamp, String code, String message) {
        return new ApiEnvelope<>(false, requestId, timestamp, null, new ApiError(code, message));
    }

    public record ApiError(
            String code,
            String message
    ) {
    }
}
