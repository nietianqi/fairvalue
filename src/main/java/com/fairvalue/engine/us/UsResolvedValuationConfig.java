package com.fairvalue.engine.us;

import java.util.List;
import java.util.Map;

public record UsResolvedValuationConfig(
        String sectorTemplate,
        List<String> primaryMethods,
        Map<String, Double> normalizedWeights,
        Map<String, Double> numericParameters,
        double defaultMarginOfSafety,
        List<String> riskNotes
) {
}
