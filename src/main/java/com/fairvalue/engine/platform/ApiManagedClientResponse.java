package com.fairvalue.engine.platform;

import java.util.List;
import java.util.Map;

public record ApiManagedClientResponse(
        long id,
        String clientKey,
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
