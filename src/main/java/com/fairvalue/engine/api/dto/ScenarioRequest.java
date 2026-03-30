package com.fairvalue.engine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ScenarioRequest(
        @NotBlank String market,
        @NotBlank String symbol,
        Double revenueGrowth,
        Double ebitMargin,
        Double wacc,
        Double terminalGrowth,
        Double targetPe,
        Double targetPb
) {
}
