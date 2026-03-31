package com.fairvalue.engine.api.dto.us;

public record UsRiskItem(
        String riskType,
        String probability,
        String impact,
        String adjustmentType,
        double adjustment,
        String note
) {
}
