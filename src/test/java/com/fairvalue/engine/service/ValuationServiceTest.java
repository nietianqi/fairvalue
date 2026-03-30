package com.fairvalue.engine.service;

import com.fairvalue.engine.api.dto.ScreenerRequest;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.valuation.ValuationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ValuationServiceTest {
    @Autowired
    private ValuationService valuationService;

    @Test
    void shouldSupportAllFourMarkets() {
        List<ValuationResult> results = List.of(
                valuationService.valuate(Market.US, "AAPL"),
                valuationService.valuate(Market.CN, "600519"),
                valuationService.valuate(Market.JP, "7203"),
                valuationService.valuate(Market.HK, "0700.HK")
        );

        assertTrue(results.stream().allMatch(result -> result.models().size() >= 3));
        assertTrue(results.stream().allMatch(result -> result.confidence() > 0));
    }

    @Test
    void screenerShouldReturnCandidates() {
        List<ValuationResult> candidates = valuationService.screener(
                new ScreenerRequest(List.of("US", "CN", "JP", "HK"), 0.0, 0.08, true, 5.0, 0.0, 10)
        );

        assertFalse(candidates.isEmpty());
    }
}
