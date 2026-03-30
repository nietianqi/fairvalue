package com.fairvalue.engine.api.dto;

import com.fairvalue.engine.valuation.ValuationResult;

import java.util.List;

public record ScreenerResponse(
        int count,
        List<ValuationResult> candidates
) {
}
