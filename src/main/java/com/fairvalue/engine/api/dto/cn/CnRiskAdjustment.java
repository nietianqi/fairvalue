package com.fairvalue.engine.api.dto.cn;

public record CnRiskAdjustment(
        String factor,
        String level,
        double adjustment,
        String note
) {
}
