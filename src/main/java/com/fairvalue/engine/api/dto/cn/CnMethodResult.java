package com.fairvalue.engine.api.dto.cn;

public record CnMethodResult(
        String methodName,
        double methodValue,
        double weight,
        String keyAssumption
) {
}
