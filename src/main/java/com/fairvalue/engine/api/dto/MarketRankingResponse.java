package com.fairvalue.engine.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarketRankingResponse(
        String market,
        String rankingType,
        int page,
        int size,
        long total,
        String source,
        Instant generatedAt,
        LocalDate dataAsOf,
        String rankingBasis,
        String disclaimer,
        List<MarketRankingItem> items
) {
}
