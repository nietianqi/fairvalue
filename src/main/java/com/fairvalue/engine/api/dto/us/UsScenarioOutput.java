package com.fairvalue.engine.api.dto.us;

public record UsScenarioOutput(
        String scenario,
        double probability,
        double targetPrice,
        double fairValueLow,
        double fairValueHigh,
        double upside
) {
}
