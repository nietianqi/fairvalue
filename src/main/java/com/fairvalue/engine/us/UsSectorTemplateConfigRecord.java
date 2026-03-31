package com.fairvalue.engine.us;

public record UsSectorTemplateConfigRecord(
        String sectorTemplate,
        String primaryMethodsJson,
        String forbiddenMethodsJson,
        String defaultWeightsJson,
        String safetyMarginRuleJson,
        String riskNotesJson
) {
}
