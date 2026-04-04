package com.fairvalue.engine.us;

import java.time.LocalDate;

public record UsConfiguredMethodValuation(
        String method,
        double bearValue,
        double baseValue,
        double bullValue,
        double weight,
        String rationale,
        LocalDate inputSnapshotDate,
        boolean primaryMethod,
        String assumptionsJson,
        String sensitivityJson,
        String notes,
        String methodStatus,
        boolean outlierTrimmed,
        boolean weightAdjustedByDataQuality
) {
    public UsConfiguredMethodValuation(
            String method,
            double bearValue,
            double baseValue,
            double bullValue,
            double weight,
            String rationale,
            LocalDate inputSnapshotDate,
            boolean primaryMethod,
            String assumptionsJson,
            String sensitivityJson,
            String notes
    ) {
        this(method, bearValue, baseValue, bullValue, weight, rationale, inputSnapshotDate, primaryMethod,
                assumptionsJson, sensitivityJson, notes, "active", false, false);
    }
}
