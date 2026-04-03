package com.fairvalue.engine.us;

import java.time.Instant;

public record UsValuationJobRecord(
        long id,
        String jobType,
        Long securityId,
        String ticker,
        String status,
        Long valuationRunId,
        int priority,
        Instant availableAt,
        String workerId,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        int retryCount,
        String payloadJson,
        Instant createdAt
) {
}
