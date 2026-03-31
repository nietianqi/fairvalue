package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsRiskItem;

import java.util.List;

public record UsRiskMatrixResult(
        List<UsRiskItem> items,
        double waccAdjustment,
        double bearProbabilityDelta,
        double bullProbabilityDelta,
        double marginOfSafetyAdjustment,
        double confidencePenalty,
        boolean valueTrapFlag
) {
}
