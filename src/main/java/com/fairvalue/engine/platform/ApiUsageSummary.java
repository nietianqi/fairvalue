package com.fairvalue.engine.platform;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ApiUsageSummary(
        String clientName,
        String planCode,
        LocalDate usageDate,
        long totalRequests,
        long dailyQuota,
        long remainingQuota,
        Instant lastSeenAt,
        List<ApiUsageRouteRecord> routes
) {
}
