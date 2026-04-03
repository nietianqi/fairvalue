package com.fairvalue.engine.platform;

import java.time.Instant;
import java.util.List;

public record ApiPlatformBootstrapResponse(
        Instant asOf,
        String apiKeyHeader,
        String apiKey,
        String envelopeHeader,
        boolean forceEnvelope,
        String clientName,
        String planCode,
        int requestsPerMinute,
        long dailyQuota,
        List<String> entitlements
) {
}
