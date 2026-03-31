package com.fairvalue.engine.us;

public record UsRiskScoreRecord(
        long securityId,
        long valuationRunId,
        String riskType,
        double probability,
        double impact,
        double score,
        String adjustmentType,
        double adjustmentValue,
        String note
) {
}
