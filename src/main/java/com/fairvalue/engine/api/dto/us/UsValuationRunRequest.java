package com.fairvalue.engine.api.dto.us;

import java.util.List;
import java.util.Map;

public record UsValuationRunRequest(
        String style,
        String horizon,
        boolean useConsensus,
        List<String> forceMethods,
        Map<String, Double> customAssumptions
) {
    public static UsValuationRunRequest defaults() {
        return new UsValuationRunRequest("balanced", "6-18m", true, List.of(), Map.of());
    }
}
