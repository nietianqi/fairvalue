package com.fairvalue.engine.valuation;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.ValuationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ValuationResult(
        Market market,
        String symbol,
        String currency,
        double price,
        LocalDate valuationDate,
        double intrinsicValue,
        double tradableFairValue,
        double fairValueLow,
        double fairValueHigh,
        double upside,
        double confidence,
        ValuationStatus valuationStatus,
        List<ModelValuation> models,
        Map<String, Double> drivers,
        List<MarketAdjustment> marketAdjustments,
        List<String> riskFlags,
        String dataVersion
) {
}
