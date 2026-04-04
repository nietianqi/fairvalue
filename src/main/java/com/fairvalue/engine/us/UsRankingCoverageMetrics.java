package com.fairvalue.engine.us;

import java.time.Instant;

public record UsRankingCoverageMetrics(
        Instant asOf,
        long universeSize,
        long snapshotCount,
        long rankableCount,
        long staleCount,
        long excludedStalePriceCount,
        long excludedBadIndustryMatchCount,
        long excludedLowConfidenceCount,
        double snapshotCoverage,
        double rankableCoverage,
        double staleRatio,
        boolean strictReady,
        String rankingMode,
        String disclaimer
) {
}
