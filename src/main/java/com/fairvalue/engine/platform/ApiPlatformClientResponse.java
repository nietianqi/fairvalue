package com.fairvalue.engine.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiPlatformClientResponse(
        Instant asOf,
        String clientName,
        String planCode,
        boolean enabled,
        boolean publicClient,
        int requestsPerMinute,
        long dailyQuota,
        List<String> entitlements,
        Map<String, Object> metadata
) {
}
