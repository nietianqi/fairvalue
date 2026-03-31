package com.fairvalue.engine.api.dto.us;

public record UsEquityProfileResponse(
        String ticker,
        String companyName,
        String exchange,
        String sector,
        String industry,
        double marketCap,
        double dilutedShares,
        String companyType,
        String sectorTemplate,
        double pe,
        double evEbitda,
        double earningsGrowth5y,
        double dailyChange
) {
}
