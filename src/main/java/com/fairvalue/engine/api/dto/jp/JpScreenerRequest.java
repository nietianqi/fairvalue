package com.fairvalue.engine.api.dto.jp;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

public record JpScreenerRequest(
        String market,
        @DecimalMin("0") Double minUndervaluedPct,
        @Min(0) Integer minConfidenceScore,
        @DecimalMin("0") Double minDividendYield,
        @DecimalMin("0") Double maxPbr,
        String industry,
        @Min(1) Integer limit
) {
}
