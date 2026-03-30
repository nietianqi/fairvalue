package com.fairvalue.engine.api.dto;

import com.fairvalue.engine.valuation.HistoryPoint;

import java.util.List;

public record HistoryResponse(
        String market,
        String symbol,
        List<HistoryPoint> points
) {
}
