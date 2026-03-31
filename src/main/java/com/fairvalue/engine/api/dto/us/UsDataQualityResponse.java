package com.fairvalue.engine.api.dto.us;

import java.util.List;

public record UsDataQualityResponse(
        String ticker,
        String latest10kDate,
        String latest10qDate,
        boolean hasRecent8k,
        boolean shareCountVerified,
        boolean sbcQuantified,
        String guidanceStatus,
        double confidenceLevel,
        List<String> missingItems,
        List<String> warningFlags
) {
}
