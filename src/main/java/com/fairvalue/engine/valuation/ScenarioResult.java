package com.fairvalue.engine.valuation;

import java.util.List;
import java.util.Map;

public record ScenarioResult(
        String market,
        String symbol,
        List<ScenarioPoint> scenarios,
        Map<String, Double> sensitivity
) {
}
