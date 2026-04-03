package com.fairvalue.engine.platform;

import java.util.List;
import java.util.Map;

public record ApiCreateClientRequest(
        String clientName,
        String planCode,
        Boolean publicClient,
        Integer requestsPerMinute,
        Long dailyQuota,
        List<String> entitlements,
        Map<String, Object> metadata
) {
}
