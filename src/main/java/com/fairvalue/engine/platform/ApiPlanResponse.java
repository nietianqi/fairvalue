package com.fairvalue.engine.platform;

import java.util.List;

public record ApiPlanResponse(
        String code,
        String displayName,
        String description,
        int requestsPerMinute,
        long dailyQuota,
        List<String> entitlements
) {
}
