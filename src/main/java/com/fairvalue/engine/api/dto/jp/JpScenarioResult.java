package com.fairvalue.engine.api.dto.jp;

public record JpScenarioResult(
        String scenario,
        double probability,
        double fairValue,
        double fairValueLow,
        double fairValueHigh,
        double marginOfSafety
) {
}
