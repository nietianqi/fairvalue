package com.fairvalue.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.api.dto.ScreenerResponse;
import com.fairvalue.engine.api.dto.ScreenerRequest;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.domain.ValuationStatus;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.repository.ValuationRunsRepository;
import com.fairvalue.engine.us.UsSecurityMasterService;
import com.fairvalue.engine.us.UsStoredValuationSnapshotRecord;
import com.fairvalue.engine.us.UsValuationReadService;
import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.valuation.ValuationResult;
import com.fairvalue.engine.valuation.strategy.MarketStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValuationServiceDiagnosticsTest {

    @Test
    void screenerShouldExposeStoredUsDiagnostics() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        StockSnapshot snapshot = new StockSnapshot(
                Market.US,
                "MUSA",
                "USD",
                "Murphy USA",
                "Fuel Retail",
                502.41,
                fundamentals(0.16, 1.9, 5.0, 0.80, 0.65, 0.55, 0.72),
                "snapshot:2026-04-02"
        );
        UsStoredValuationSnapshotRecord stored = new UsStoredValuationSnapshotRecord(
                1L,
                "MUSA",
                1001L,
                Instant.parse("2026-04-03T00:00:00Z"),
                502.41,
                520.0,
                560.0,
                610.0,
                0.12,
                0.63,
                0.45,
                "UNDERVALUED WITH MARGIN",
                "market is pricing in lower normalized fuel margins",
                true,
                "fuel_retail",
                "general_quality",
                0.71,
                0.52,
                "2026Q1|mult:template|mcap:profile",
                LocalDate.parse("2026-04-02"),
                6,
                "research fallback",
                false,
                "research_only",
                "missing price verification",
                "industry_curated",
                0.61,
                true,
                "{}",
                "{\"data_quality_audit\":{\"missing_items\":[\"standardized_financials_missing\"],\"normalization_review\":{\"owner_earnings\":\"manual_review_required\",\"working_capital\":\"not_structured\",\"capex_split\":\"reported_total_capex_only\",\"one_time_items\":\"manual_review_required\"}}}",
                "{\"peer_set_context_only\":true,\"relative_anchor_mode\":\"configured_template_with_peer_context\"}"
        );

        when(marketDataService.listSnapshots(List.of(Market.US))).thenReturn(List.of(snapshot));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of(stored));

        List<ValuationResult> results = service.screener(ScreenerRequest.basic(List.of("US"), 0.0, 0.0, false, 10.0, 0.0, 10));

        assertThat(results).hasSize(1);
        ValuationResult result = results.get(0);
        assertThat(result.drivers())
                .containsEntry("data_quality_score", 0.52)
                .containsEntry("quality_score", 0.71)
                .containsEntry("margin_of_safety", 0.45)
                .containsEntry("confidence_level", 0.63)
                .containsEntry("price_freshness_days", 6.0)
                .containsEntry("normalization_gap_count", 4.0)
                .containsEntry("missing_item_count", 1.0);
        assertThat(result.riskFlags())
                .contains(
                        "data_quality_low",
                        "confidence_medium",
                        "price_source_research_fallback",
                        "price_stale",
                        "value_trap",
                        "current_multiple_template",
                        "market_cap_profile",
                        "not_rankable",
                        "industry_fallback_used",
                        "peer_context_only",
                        "relative_anchor_peer_context",
                        "standardized_financials_missing",
                        "owner_earnings_not_structured",
                        "working_capital_not_structured",
                        "capex_split_not_structured",
                        "one_time_items_manual_review"
                );
    }

    @Test
    void screenerShouldExposeQuickSnapshotDiagnosticsWhenStoredSnapshotIsMissing() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        StockSnapshot snapshot = new StockSnapshot(
                Market.US,
                "AMTM",
                "USD",
                "Amentum Holdings",
                "Government Services",
                27.04,
                fundamentals(0.14, 2.1, 12.0, 0.65, 0.50, 0.35, 0.60),
                "2026Q1|mult:template|mcap:profile"
        );

        when(marketDataService.listSnapshots(List.of(Market.US))).thenReturn(List.of(snapshot));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of());

        List<ValuationResult> results = service.screener(ScreenerRequest.basic(List.of("US"), 0.0, 0.0, false, 10.0, 0.0, 10));

        assertThat(results).hasSize(1);
        ValuationResult result = results.get(0);
        assertThat(result.drivers()).containsKeys("confidence_level", "quality_multiplier", "valuation_band");
        assertThat(result.riskFlags())
                .contains("quick_snapshot_only", "confidence_medium", "current_multiple_template", "market_cap_profile");
        assertThat(result.upside()).isGreaterThan(0.0);
    }

    @Test
    void screenerShouldPreferUpsideStatusWhenStoredVerdictLooksInconsistent() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        StockSnapshot snapshot = new StockSnapshot(
                Market.US,
                "TGT",
                "USD",
                "Target",
                "Retail",
                104.7,
                fundamentals(0.15, 4.0, 10.0, 0.60, 0.45, 0.25, 0.58),
                "2026Q1"
        );
        UsStoredValuationSnapshotRecord stored = new UsStoredValuationSnapshotRecord(
                2L,
                "TGT",
                1002L,
                Instant.parse("2026-04-05T00:00:00Z"),
                104.7,
                149.18,
                187.67,
                242.72,
                0.79,
                0.45,
                0.19,
                "FAIR",
                "market is pricing in lower normalized earnings power",
                false,
                "general_quality",
                "general_quality",
                0.54,
                0.20,
                "sec:2025-11-26",
                LocalDate.parse("2026-04-05"),
                130,
                "research fallback",
                false,
                "research_only",
                "missing price verification",
                "industry_curated",
                0.51,
                true,
                "{}",
                "{}",
                "{}"
        );

        when(marketDataService.listSnapshots(List.of(Market.US))).thenReturn(List.of(snapshot));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of(stored));

        List<ValuationResult> results = service.screener(ScreenerRequest.basic(List.of("US"), 0.0, 0.0, false, 10.0, 0.0, 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).valuationStatus()).isEqualTo(ValuationStatus.DEEP_UNDERVALUE);
    }

    @Test
    void screenerResponseShouldFilterOutIncidentalSubstringMatches() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        when(usSecurityMasterService.findActiveUsUniverse()).thenReturn(List.of(
                new UsSecurityMaster(1L, "PAPL", "PAPL.US", "Pineapple Financial Inc.", "US", "USD", null, "General", null, null, null, "US", true),
                new UsSecurityMaster(2L, "AAPL", "AAPL.US", "Apple Inc.", "Nasdaq", "USD", "Technology", "Electronic Computers", null, "compounder", "us_tech_compounder", "US", true),
                new UsSecurityMaster(3L, "MLP", "MLP.US", "Maui Land & Pineapple Co Inc", "US", "USD", null, "General", null, null, null, "US", true)
        ));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of());
        when(marketDataService.listSnapshots(List.of())).thenReturn(List.of());
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "PAPL")).thenReturn(
                snapshot("PAPL", "Pineapple Financial Inc.", "General", 201.7, fundamentals(0.23, 4.83, 41.68, 0.92, 0.85, 0.89, 0.86))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "AAPL")).thenReturn(
                snapshot("AAPL", "Apple Inc.", "Electronic Computers", 255.92, fundamentals(0.31, 44.0, 7.0, 0.95, 0.92, 0.98, 0.84))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "MLP")).thenReturn(
                snapshot("MLP", "Maui Land & Pineapple Co Inc", "General", 347.1, fundamentals(0.13, 2.37, 19.84, 0.70, 0.55, 0.48, 0.59))
        );

        ScreenerResponse response = service.screenerResponse(new ScreenerRequest(
                List.of("US"),
                "apple",
                0.0,
                0.0,
                false,
                100.0,
                0.0,
                0.0,
                10000.0,
                0.0,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                10
        ));

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    void screenerResponseShouldReturnCandidatesWhenQueryIsBlank() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        when(usSecurityMasterService.findActiveUsUniverse()).thenReturn(List.of(
                new UsSecurityMaster(1L, "PAPL", "PAPL.US", "Pineapple Financial Inc.", "US", "USD", null, "General", null, null, null, "US", true),
                new UsSecurityMaster(2L, "AAPL", "AAPL.US", "Apple Inc.", "Nasdaq", "USD", "Technology", "Electronic Computers", null, "compounder", "us_tech_compounder", "US", true),
                new UsSecurityMaster(3L, "MLP", "MLP.US", "Maui Land & Pineapple Co Inc", "US", "USD", null, "General", null, null, null, "US", true)
        ));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of());
        when(marketDataService.listSnapshots(List.of())).thenReturn(List.of());
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "PAPL")).thenReturn(
                snapshot("PAPL", "Pineapple Financial Inc.", "General", 201.7, fundamentals(0.23, 4.83, 41.68, 0.92, 0.85, 0.89, 0.86))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "AAPL")).thenReturn(
                snapshot("AAPL", "Apple Inc.", "Electronic Computers", 255.92, fundamentals(0.31, 44.0, 7.0, 0.95, 0.92, 0.98, 0.84))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "MLP")).thenReturn(
                snapshot("MLP", "Maui Land & Pineapple Co Inc", "General", 347.1, fundamentals(0.13, 2.37, 19.84, 0.70, 0.55, 0.48, 0.59))
        );

        ScreenerResponse response = service.screenerResponse(new ScreenerRequest(
                List.of("US"),
                null,
                0.0,
                0.0,
                false,
                100.0,
                0.0,
                0.0,
                10000.0,
                0.0,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                10
        ));

        assertThat(response.count()).isEqualTo(3);
        assertThat(response.candidates()).hasSize(3);
    }

    @Test
    void screenerResponseShouldExcludeSyntheticUsCandidatesWithoutStoredValuation() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        when(usSecurityMasterService.findActiveUsUniverse()).thenReturn(List.of(
                new UsSecurityMaster(1L, "REAL", "REAL.US", "Real Coverage Inc.", "Nasdaq", "USD", "Technology", "Software", null, "compounder", "us_software", "US", true),
                new UsSecurityMaster(2L, "SYN", "SYN.US", "Synthetic Coverage Inc.", "NYSE", "USD", "Industrials", "Capital Goods", null, "cyclical", "us_cyclical", "US", true)
        ));
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of());
        when(marketDataService.listSnapshots(List.of())).thenReturn(List.of());
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "REAL")).thenReturn(
                snapshot("REAL", "Real Coverage Inc.", "Software", 88.0, fundamentals(0.18, 3.2, 5.0, 0.94, 0.82, 0.73, 0.86))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "SYN")).thenReturn(
                snapshot("SYN", "Synthetic Coverage Inc.", "Capital Goods", 41.0, fundamentals(0.09, 1.8, 12.0, 0.66, 0.44, 0.30, 0.42), "synthetic-2026Q1")
        );

        ScreenerResponse response = service.screenerResponse(new ScreenerRequest(
                List.of("US"),
                null,
                0.0,
                0.0,
                false,
                100.0,
                0.0,
                0.0,
                10000.0,
                0.0,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                10
        ));

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).symbol()).isEqualTo("REAL");
        assertThat(response.candidates().get(0).syntheticSnapshot()).isFalse();
    }

    @Test
    void screenerResponseShouldApplyDecisionGradeFilters() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketStrategyRegistry strategyRegistry = mock(MarketStrategyRegistry.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        MarketPriceDailyRepository marketPriceDailyRepository = mock(MarketPriceDailyRepository.class);
        ValuationRunsRepository valuationRunsRepository = mock(ValuationRunsRepository.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        UsValuationReadService usValuationReadService = new UsValuationReadService(
                valuationLatestSnapshotRepository,
                new ObjectMapper(),
                marketDataService
        );

        ValuationService service = new ValuationService(
                marketDataService,
                strategyRegistry,
                usSecurityMasterService,
                marketPriceDailyRepository,
                valuationRunsRepository,
                valuationLatestSnapshotRepository,
                usValuationReadService
        );

        when(usSecurityMasterService.findActiveUsUniverse()).thenReturn(List.of(
                new UsSecurityMaster(10L, "SAFE", "SAFE.US", "Safe Compounder", "Nasdaq", "USD", "Technology", "Software", null, "compounder", "us_software", "US", true),
                new UsSecurityMaster(20L, "TRAP", "TRAP.US", "Trap Industries", "NYSE", "USD", "Industrials", "Capital Goods", null, "cyclical", "us_cyclical", "US", true)
        ));
        when(marketDataService.listSnapshots(List.of())).thenReturn(List.of());
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "SAFE")).thenReturn(
                snapshot("SAFE", "Safe Compounder", "Software", 82.0, fundamentals(0.22, 3.1, 2.0, 0.96, 0.82, 0.76, 0.87))
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "TRAP")).thenReturn(
                snapshot("TRAP", "Trap Industries", "Capital Goods", 31.0, fundamentals(0.05, 1.2, 24.0, 0.58, 0.28, 0.18, 0.31))
        );
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of(
                new UsStoredValuationSnapshotRecord(
                        10L,
                        "SAFE",
                        2001L,
                        Instant.parse("2026-04-07T00:00:00Z"),
                        82.0,
                        88.0,
                        105.0,
                        118.0,
                        0.28,
                        0.72,
                        0.22,
                        "UNDERVALUED",
                        "market is discounting a slower medium-term growth path",
                        false,
                        "us_software",
                        "compounder",
                        0.78,
                        0.81,
                        "2026Q1|mult:live|mcap:live",
                        LocalDate.parse("2026-04-07"),
                        2,
                        "live",
                        true,
                        "rankable",
                        null,
                        "industry_curated",
                        0.88,
                        false,
                        "{}",
                        "{}",
                        "{}"
                ),
                new UsStoredValuationSnapshotRecord(
                        20L,
                        "TRAP",
                        2002L,
                        Instant.parse("2026-04-07T00:00:00Z"),
                        31.0,
                        29.0,
                        42.0,
                        52.0,
                        0.35,
                        0.33,
                        0.18,
                        "UNDERVALUED",
                        "market is discounting a sharp margin recovery",
                        true,
                        "us_cyclical",
                        "cyclical",
                        0.39,
                        0.27,
                        "2026Q1|mult:template|mcap:profile",
                        LocalDate.parse("2026-03-18"),
                        20,
                        "research fallback",
                        false,
                        "research_only",
                        "missing price verification",
                        "industry_curated",
                        0.42,
                        true,
                        "{}",
                        "{}",
                        "{}"
                )
        ));

        ScreenerResponse response = service.screenerResponse(new ScreenerRequest(
                List.of("US"),
                null,
                0.0,
                0.0,
                false,
                100.0,
                0.0,
                0.0,
                10000.0,
                0.0,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0.10,
                0.65,
                List.of("LOW", "MEDIUM"),
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                10
        ));

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).symbol()).isEqualTo("SAFE");
        assertThat(response.candidates().get(0).marginOfSafety()).isEqualTo(0.22);
        assertThat(response.candidates().get(0).qualityScore()).isEqualTo(0.78);
        assertThat(response.candidates().get(0).riskLevel()).isEqualTo("LOW");
        assertThat(response.candidates().get(0).valueTrapFlag()).isFalse();
    }

    private StockFundamentals fundamentals(
            double roe,
            double pb,
            double dataFreshnessDays,
            double dataCompleteness,
            double liquidityScore,
            double analystCoverage,
            double governanceScore
    ) {
        return new StockFundamentals(
                0.08,
                0.12,
                0.09,
                0.02,
                roe,
                12.0,
                pb,
                8.0,
                0.02,
                0.01,
                0.01,
                0.0,
                0.05,
                liquidityScore,
                0.0,
                0.02,
                0.14,
                dataFreshnessDays,
                dataCompleteness,
                analystCoverage,
                0.0,
                governanceScore,
                true
        );
    }

    private StockSnapshot snapshot(
            String symbol,
            String companyName,
            String industry,
            double price,
            StockFundamentals fundamentals
    ) {
        return snapshot(symbol, companyName, industry, price, fundamentals, "2026Q1");
    }

    private StockSnapshot snapshot(
            String symbol,
            String companyName,
            String industry,
            double price,
            StockFundamentals fundamentals,
            String dataVersion
    ) {
        return new StockSnapshot(
                Market.US,
                symbol,
                "USD",
                companyName,
                industry,
                price,
                fundamentals,
                dataVersion
        );
    }
}
