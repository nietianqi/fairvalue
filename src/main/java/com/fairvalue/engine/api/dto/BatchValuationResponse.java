package com.fairvalue.engine.api.dto;

import com.fairvalue.engine.valuation.ValuationResult;

import java.util.List;

public record BatchValuationResponse(
        int count,
        List<ValuationResult> results
) {
}
