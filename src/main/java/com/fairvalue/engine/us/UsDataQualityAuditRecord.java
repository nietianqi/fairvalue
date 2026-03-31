package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsDataQualityAuditRecord(
        long securityId,
        LocalDate auditDate,
        LocalDate latest10kDate,
        LocalDate latest10qDate,
        boolean hasRecent8k,
        boolean shareCountVerified,
        boolean sbcQuantified,
        String guidanceStatus,
        boolean netDebtUpdated,
        boolean taxRateUpdated,
        boolean litigationCaptured,
        boolean convertibleIdentified,
        BigDecimal dataQualityScore,
        BigDecimal confidenceLevel,
        String missingItemsJson,
        String warningFlagsJson
) {
}
