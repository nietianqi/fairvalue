package com.fairvalue.engine.api.dto.us;

public record UsMethodOutput(
        String method,
        double bearValue,
        double baseValue,
        double bullValue,
        double weight,
        String rationale
) {
}
