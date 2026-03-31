package com.fairvalue.engine.us;

public record UsValuationParameterRecord(
        Long securityId,
        String sectorTemplate,
        String parameterType,
        String parameterKey,
        String parameterValueJson
) {
}
