package com.fairvalue.engine.us;

import java.time.LocalDate;

public record UsValuationMethodResultRecord(
        long valuationRunId,
        long securityId,
        String methodName,
        double bearValue,
        double baseValue,
        double bullValue,
        double weight,
        boolean primaryMethod,
        LocalDate inputSnapshotDate,
        String assumptionsJson,
        String sensitivityJson,
        String notes
) {
}
