package com.fairvalue.engine.platform;

import java.time.Instant;
import java.time.LocalDate;

public record ApiUsageRouteRecord(
        LocalDate usageDate,
        String routeKey,
        String httpMethod,
        String statusBucket,
        long requestCount,
        Instant lastSeenAt
) {
}
