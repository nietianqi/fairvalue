package com.fairvalue.engine.us;

import java.util.List;

public record UsPeerUniverseRuleProfile(
        String ruleId,
        String ruleVersion,
        List<String> basisOrder,
        List<String> filterMetrics,
        int candidateLimitMultiplier,
        int minPeersRequired,
        double fetchMinFcfMargin,
        double fetchMinRoic,
        double strictMarketCapMinRatio,
        double strictMarketCapMaxRatio,
        Double strictRevenueGrowthTolerance,
        Double strictFcfMarginTolerance,
        Double strictRoicTolerance,
        Double strictPeTolerance,
        Double strictPbTolerance,
        double relaxedMarketCapMinRatio,
        double relaxedMarketCapMaxRatio,
        Double relaxedRevenueGrowthTolerance,
        Double relaxedFcfMarginTolerance,
        Double relaxedRoicTolerance,
        Double relaxedPeTolerance,
        Double relaxedPbTolerance
) {
}
