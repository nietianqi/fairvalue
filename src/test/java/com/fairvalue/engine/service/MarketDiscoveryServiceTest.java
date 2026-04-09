package com.fairvalue.engine.service;

import com.fairvalue.engine.api.dto.MarketRankingResponse;
import com.fairvalue.engine.cn.CnStockValuationService;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import com.fairvalue.engine.us.UsConfiguredValuationModelsService;
import com.fairvalue.engine.us.UsEquityValuationService;
import com.fairvalue.engine.us.UsPeerUniverseRulesService;
import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.us.UsSecurityMasterService;
import com.fairvalue.engine.us.UsStoredValuationSnapshotRecord;
import com.fairvalue.engine.us.UsValuationReadService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDiscoveryServiceTest {

    @Test
    void fallbackUsRankingsShouldSortGloballyBeforePagingAndUseLiquidityForActivity() {
        CnStockValuationService cnStockValuationService = mock(CnStockValuationService.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        ValuationService valuationService = mock(ValuationService.class);
        UsEquityValuationService usEquityValuationService = mock(UsEquityValuationService.class);
        UsValuationReadService usValuationReadService = mock(UsValuationReadService.class);
        UsConfiguredValuationModelsService usConfiguredValuationModelsService = mock(UsConfiguredValuationModelsService.class);
        UsPeerUniverseRulesService usPeerUniverseRulesService = mock(UsPeerUniverseRulesService.class);
        UsSecurityMasterService usSecurityMasterService = mock(UsSecurityMasterService.class);
        ValuationLatestSnapshotRepository valuationLatestSnapshotRepository = mock(ValuationLatestSnapshotRepository.class);
        MarketSnapshotRepository marketSnapshotRepository = mock(MarketSnapshotRepository.class);
        FinancialDerivedMetricsRepository financialDerivedMetricsRepository = mock(FinancialDerivedMetricsRepository.class);

        MarketDiscoveryService service = new MarketDiscoveryService(
                cnStockValuationService,
                marketDataService,
                valuationService,
                usEquityValuationService,
                usValuationReadService,
                usConfiguredValuationModelsService,
                usPeerUniverseRulesService,
                usSecurityMasterService,
                valuationLatestSnapshotRepository,
                marketSnapshotRepository,
                financialDerivedMetricsRepository
        );
        ReflectionTestUtils.setField(service, "strictMinCoverageRatio", 2.0d);

        List<UsSecurityMaster> universe = List.of(
                new UsSecurityMaster(1L, "AAA", "AAA.US", "Alpha Apps", "Nasdaq", "USD", "Technology", "Software", null, "compounder", "us_software", "US", true),
                new UsSecurityMaster(2L, "BBB", "BBB.US", "Bravo Bits", "Nasdaq", "USD", "Technology", "Software", null, "compounder", "us_software", "US", true),
                new UsSecurityMaster(3L, "CCC", "CCC.US", "Core Cloud", "NYSE", "USD", "Technology", "Software", null, "compounder", "us_software", "US", true)
        );

        when(usSecurityMasterService.countActiveUsUniverse()).thenReturn(3L);
        when(usSecurityMasterService.findActiveUsUniverse()).thenReturn(universe);
        when(valuationLatestSnapshotRepository.countByMarket(Market.US.name())).thenReturn(3L);
        when(valuationLatestSnapshotRepository.countStaleByMarket(eq(Market.US.name()), any(Instant.class))).thenReturn(0L);
        when(valuationLatestSnapshotRepository.countRankableByMarket(eq(Market.US.name()), any(Instant.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(3L);
        when(valuationLatestSnapshotRepository.countExcludedByReason(Market.US.name(), "stale_price")).thenReturn(0L);
        when(valuationLatestSnapshotRepository.countIndustryFallbackExcludedByMarket(Market.US.name())).thenReturn(0L);
        when(valuationLatestSnapshotRepository.countLowConfidenceExcludedByMarket(Market.US.name(), 0.45)).thenReturn(0L);
        when(valuationLatestSnapshotRepository.findAllByMarket(Market.US.name())).thenReturn(List.of(
                storedSnapshot(1L, "AAA", 0.10, 0.41),
                storedSnapshot(2L, "BBB", 0.35, 0.32),
                storedSnapshot(3L, "CCC", 0.20, 0.55)
        ));

        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "AAA")).thenReturn(
                snapshot("AAA", "Alpha Apps", "Software", 101.0, 0.33)
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "BBB")).thenReturn(
                snapshot("BBB", "Bravo Bits", "Software", 98.0, 0.81)
        );
        when(marketDataService.getCachedOrSyntheticSnapshot(Market.US, "CCC")).thenReturn(
                snapshot("CCC", "Core Cloud", "Software", 95.0, 0.58)
        );

        MarketRankingResponse firstPage = service.rankings(Market.US, "undervalued", 1, 1);
        MarketRankingResponse secondPage = service.rankings(Market.US, "undervalued", 2, 1);

        assertThat(firstPage.source()).isEqualTo("us_security_master_cached_global_ranking");
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.items().get(0).ticker()).isEqualTo("BBB");
        assertThat(firstPage.items().get(0).activityMetric()).isEqualTo(81.0);

        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().get(0).ticker()).isEqualTo("CCC");
    }

    private UsStoredValuationSnapshotRecord storedSnapshot(long securityId, String ticker, double upsidePct, double confidence) {
        return new UsStoredValuationSnapshotRecord(
                securityId,
                ticker,
                9000L + securityId,
                Instant.parse("2026-04-08T00:00:00Z"),
                100.0,
                105.0,
                112.0,
                120.0,
                upsidePct,
                confidence,
                0.18,
                "UNDERVALUED",
                "market is discounting slower normalized growth",
                false,
                "us_software",
                "compounder",
                0.74,
                0.79,
                "2026Q1|mult:vendor|mcap:vendor",
                LocalDate.parse("2026-04-08"),
                1,
                "market_data",
                true,
                "rankable",
                null,
                "industry_direct",
                0.88,
                false,
                "{}",
                "{}",
                "{}"
        );
    }

    private StockSnapshot snapshot(
            String symbol,
            String companyName,
            String industry,
            double price,
            double liquidityScore
    ) {
        return new StockSnapshot(
                Market.US,
                symbol,
                "USD",
                companyName,
                industry,
                price,
                fundamentals(liquidityScore),
                "2026Q1"
        );
    }

    private StockFundamentals fundamentals(double liquidityScore) {
        return new StockFundamentals(
                0.10,
                0.14,
                0.09,
                0.02,
                0.18,
                16.0,
                2.8,
                9.0,
                0.01,
                0.0,
                0.03,
                0.05,
                0.10,
                liquidityScore,
                0.0,
                0.02,
                0.12,
                5.0,
                0.92,
                0.70,
                0.0,
                0.84,
                true
        );
    }
}
