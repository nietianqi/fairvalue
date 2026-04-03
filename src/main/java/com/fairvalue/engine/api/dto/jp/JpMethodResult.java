package com.fairvalue.engine.api.dto.jp;

public record JpMethodResult(
        String method,
        double value,
        double weight,
        String rationale
) {
}
