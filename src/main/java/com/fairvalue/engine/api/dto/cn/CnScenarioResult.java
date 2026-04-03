package com.fairvalue.engine.api.dto.cn;

public record CnScenarioResult(
        String scenario,
        double probability,
        double fairValueLow,
        double fairValueMid,
        double fairValueHigh
) {
}
