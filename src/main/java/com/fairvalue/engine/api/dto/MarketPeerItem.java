package com.fairvalue.engine.api.dto;

public record MarketPeerItem(
        String ticker,
        String companyName,
        String industry,
        String matchBasis,
        Double currentPrice,
        Double fairValue,
        Double upside,
        Double pe,
        Double evEbitda,
        Double marketCap,
        Double revenueGrowth,
        Double fcfMargin,
        Double roic,
        Double confidenceLevel,
        Double qualityScore,
        Double peerQualityScore,
        Double peerDataCompleteness,
        String verdict
) {
}
