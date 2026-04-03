package com.fairvalue.engine.api.dto.cn;

import java.util.List;

public record CnRulesVersionResponse(
        String name,
        String version,
        int minimumModelCount,
        List<String> requiredScenarios,
        String formula
) {
}
