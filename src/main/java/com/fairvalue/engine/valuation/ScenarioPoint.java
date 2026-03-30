package com.fairvalue.engine.valuation;

public record ScenarioPoint(
        String name,
        double tradableFairValue,
        double fairValueLow,
        double fairValueHigh,
        double upside
) {
}
