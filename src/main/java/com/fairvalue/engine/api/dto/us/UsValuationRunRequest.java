package com.fairvalue.engine.api.dto.us;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record UsValuationRunRequest(
        @NotBlank String style,
        @NotBlank String horizon,
        boolean useConsensus,
        List<String> forceMethods,
        Map<String, Double> customAssumptions
) {
}
