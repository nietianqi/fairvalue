package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsDataQualityResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.SecurityMasterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "market-data.us.live.universe-sync-on-startup=false",
        "market-data.us.live.enabled=false",
        "market-data.us.longbridge.enabled=false",
        "market-data.us.fred.enabled=false",
        "market-data.us.damodaran.enabled=false"
})
class UsRelativePeerSelectionTest {
    private static final List<String> CUSTOM_TICKERS = List.of("ZZREVQUAL", "ZZREVNULL", "ZZREVLOW");

    @Autowired
    private UsEquityValuationService usEquityValuationService;

    @Autowired
    private UsConfiguredValuationModelsService usConfiguredValuationModelsService;

    @Autowired
    private SecurityMasterRepository securityMasterRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private long stooqSourceId;

    @BeforeEach
    void setUp() {
        stooqSourceId = jdbcClient.sql("""
                        SELECT id
                        FROM fairvalue.source_registry
                        WHERE source_name = 'stooq'
                        """)
                .query(Long.class)
                .single();
        deleteCustomPeers();
    }

    @AfterEach
    void tearDown() {
        deleteCustomPeers();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildSourceAttributionShouldAllowMissingTargetMultipleSource() {
        StockSnapshot snapshot = sampleSnapshot("AAPL", "longbridge:2026-03-31|stooq:2026-03-30|sec:2026-01-30");
        String assumptionsJson = """
                {
                  "parameter_sources": {
                    "target_ev_ebitda": "damodaran_ev_ebitda:Computers/Peripherals"
                  },
                  "peer_set_source": "template_only",
                  "peer_selection_basis": "template_only",
                  "relative_source_mode": "template_only",
                  "peer_candidate_count": 12,
                  "peer_selection_rule_version": "v2_strict",
                  "peer_filter_summary": "basis=industry, selected=0, mode=strict",
                  "peer_filter_metrics": ["market_cap", "revenue_growth"],
                  "effective_target_ev_ebitda_source": "configured_template",
                  "peer_set_tickers": [],
                  "peer_selection_breakdown": {}
                }
                """;
        List<UsConfiguredMethodValuation> methods = List.of(
                new UsConfiguredMethodValuation(
                        "relative_valuation",
                        100.0,
                        110.0,
                        120.0,
                        0.25,
                        "test",
                        LocalDate.of(2026, 3, 31),
                        true,
                        assumptionsJson,
                        "{}",
                        "test"
                )
        );
        UsDataQualityResponse dataQuality = new UsDataQualityResponse(
                "AAPL",
                "2025-09-27",
                "2025-12-27",
                true,
                true,
                true,
                "company_ir_recent",
                0.94,
                List.of(),
                List.of()
        );

        Map<String, Object> attribution = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                usEquityValuationService,
                "buildSourceAttribution",
                snapshot,
                methods,
                dataQuality
        );

        assertThat(attribution).isNotNull();
        assertThat(attribution.get("price_source")).isEqualTo("longbridge:2026-03-31");
        assertThat(attribution.get("peer_set_source")).isEqualTo("template_only");
        assertThat(attribution.get("peer_selection_basis")).isEqualTo("template_only");
        assertThat(attribution.get("relative_source_mode")).isEqualTo("template_only");
        assertThat(attribution.get("source_attribution_version")).isEqualTo("v2");
        assertThat(attribution.get("peer_candidate_count")).isEqualTo(12);
        assertThat(attribution.get("peer_selection_rule_version")).isEqualTo("v2_strict");
        assertThat(attribution.get("peer_filter_summary")).isEqualTo("basis=industry, selected=0, mode=strict");
        assertThat((List<String>) attribution.get("peer_filter_metrics")).containsExactly("market_cap", "revenue_growth");

        Map<String, String> targetMultipleSources = (Map<String, String>) attribution.get("target_multiple_sources");
        assertThat(targetMultipleSources)
                .containsEntry("target_ev_ebitda", "damodaran_ev_ebitda:Computers/Peripherals")
                .containsKey("target_pe");
        assertThat(targetMultipleSources.get("target_pe")).isNull();
        assertThat((Map<String, String>) attribution.get("effective_target_multiple_sources"))
                .containsEntry("target_ev_ebitda", "configured_template");
    }

    @Test
    @SuppressWarnings("unchecked")
    void peerSelectionBasisShouldReturnMixedWhenPeerBasketUsesFallbackLevels() {
        UsSecurityMaster security = new UsSecurityMaster(
                1L,
                "TEST",
                "TEST.US",
                "Test Security",
                "NASDAQ",
                "USD",
                "Technology",
                "Consumer Electronics",
                null,
                "compounder",
                "us_tech_compounder",
                "US",
                true
        );
        UsValuationModelContext context = new UsValuationModelContext(
                1L,
                sampleSnapshot("TEST", "stooq:2026-03-31"),
                security,
                null,
                new UsResolvedValuationConfig(
                        "us_tech_compounder",
                        List.of("relative_valuation"),
                        Map.of("relative_valuation", 1.0),
                        Map.of(),
                        0.25,
                        List.of(),
                        Map.of()
                ),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        List<UsRelativePeerComparable> peers = List.of(
                peer("PEERIND", "Technology", "Consumer Electronics", "defensive", "other_template"),
                peer("PEERTPL", "Other Sector", "Different Industry", "different_type", "us_tech_compounder"),
                peer("PEERSEC", "Technology", "Different Industry", "different_type", "different_template")
        );

        String basis = (String) ReflectionTestUtils.invokeMethod(
                usConfiguredValuationModelsService,
                "peerSelectionBasis",
                context,
                peers
        );
        Map<String, Long> breakdown = (Map<String, Long>) ReflectionTestUtils.invokeMethod(
                usConfiguredValuationModelsService,
                "peerSelectionBreakdown",
                context,
                peers
        );

        assertThat(basis).isEqualTo("mixed");
        assertThat(breakdown)
                .containsEntry("industry", 1L)
                .containsEntry("sector_template", 1L)
                .containsEntry("sector", 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void strictPeerFiltersShouldExcludeRevenueGrowthOutliers() {
        UsSecurityMaster security = new UsSecurityMaster(
                1L,
                "TEST",
                "TEST.US",
                "Test Security",
                "NASDAQ",
                "USD",
                "Technology",
                "Consumer Electronics",
                null,
                "compounder",
                "us_tech_compounder",
                "US",
                true
        );
        UsValuationModelContext context = new UsValuationModelContext(
                1L,
                sampleSnapshot("TEST", "stooq:2026-03-31"),
                security,
                null,
                new UsResolvedValuationConfig(
                        "us_tech_compounder",
                        List.of("relative_valuation"),
                        Map.of("relative_valuation", 1.0),
                        Map.of(),
                        0.25,
                        List.of(),
                        Map.of()
                ),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(marketSnapshot(new BigDecimal("1000000")))
        );
        List<UsRelativePeerComparable> peers = List.of(
                peer("PEERNEAR", "Technology", "Consumer Electronics", "compounder", "us_tech_compounder", new BigDecimal("0.11")),
                peer("PEERFAR", "Technology", "Consumer Electronics", "compounder", "us_tech_compounder", new BigDecimal("0.42"))
        );

        List<UsRelativePeerComparable> filtered = (List<UsRelativePeerComparable>) ReflectionTestUtils.invokeMethod(
                usConfiguredValuationModelsService,
                "applyStrictPeerFilters",
                context,
                peers,
                8
        );

        assertThat(filtered)
                .extracting(UsRelativePeerComparable::ticker)
                .containsExactly("PEERNEAR");
    }

    @Test
    void findRelativePeersShouldRequireQualityMetricsWhenQualityFilterIsEnabled() {
        insertPeer("ZZREVQUAL", new BigDecimal("0.06"), new BigDecimal("0.08"));
        insertPeer("ZZREVNULL", null, null);
        insertPeer("ZZREVLOW", new BigDecimal("0.01"), new BigDecimal("0.02"));

        List<UsRelativePeerComparable> peers = securityMasterRepository.findRelativePeers(
                0L,
                "Review Technology",
                "Review Devices",
                "review_compounder",
                "review_template",
                10,
                0.03,
                0.05
        );

        assertThat(peers)
                .extracting(UsRelativePeerComparable::ticker)
                .containsExactly("ZZREVQUAL");
    }

    private void insertPeer(String ticker, BigDecimal fcfMargin, BigDecimal roic) {
        long securityId = securityMasterRepository.insert(new UsSecurityMaster(
                null,
                ticker,
                ticker + ".US",
                ticker + " Corp",
                "NASDAQ",
                "USD",
                "Review Technology",
                "Review Devices",
                null,
                "review_compounder",
                "review_template",
                "US",
                true
        ));

        Timestamp snapshotTime = Timestamp.from(Instant.parse("2026-03-31T00:00:00Z"));
        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_snapshot (
                            security_id,
                            snapshot_time,
                            last_price,
                            prev_close,
                            open_price,
                            high_price,
                            low_price,
                            volume,
                            turnover,
                            market_cap_vendor,
                            pe_ttm_vendor,
                            pb_vendor,
                            dividend_yield_vendor,
                            eps_ttm_vendor,
                            bps_vendor,
                            float_shares_vendor,
                            total_shares_vendor,
                            source_id
                        ) VALUES (
                            :securityId,
                            :snapshotTime,
                            100,
                            99,
                            99,
                            101,
                            98,
                            1000,
                            100000,
                            1000000,
                            20,
                            4,
                            0.01,
                            5,
                            25,
                            10000,
                            10000,
                            :sourceId
                        )
                        """)
                .param("securityId", securityId)
                .param("snapshotTime", snapshotTime)
                .param("sourceId", stooqSourceId)
                .update();

        jdbcClient.sql("""
                        INSERT INTO fairvalue.financial_standardized (
                            security_id,
                            period_type,
                            fiscal_year,
                            fiscal_period,
                            period_end,
                            ebitda,
                            net_debt,
                            quality_flag_json
                        ) VALUES (
                            :securityId,
                            'TTM',
                            2025,
                            'TTM',
                            DATE '2025-12-31',
                            100,
                            10,
                            '{}'::jsonb
                        )
                        """)
                .param("securityId", securityId)
                .update();

        if (fcfMargin != null && roic != null) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.financial_derived_metrics (
                                security_id,
                                period_type,
                                fiscal_year,
                                fiscal_period,
                                period_end,
                                fcf_margin,
                                roic
                            ) VALUES (
                                :securityId,
                                'TTM',
                                2025,
                                'TTM',
                                DATE '2025-12-31',
                                :fcfMargin,
                                :roic
                            )
                            """)
                    .param("securityId", securityId)
                    .param("fcfMargin", fcfMargin)
                    .param("roic", roic)
                    .update();
        }
    }

    private void deleteCustomPeers() {
        jdbcClient.sql("""
                        DELETE FROM fairvalue.security_master
                        WHERE ticker IN (:tickers)
                        """)
                .param("tickers", CUSTOM_TICKERS)
                .update();
    }

    private StockSnapshot sampleSnapshot(String ticker, String dataVersion) {
        return new StockSnapshot(
                Market.US,
                ticker,
                "USD",
                ticker + " Inc.",
                "Technology",
                100.0,
                new StockFundamentals(
                        0.12,
                        0.22,
                        0.085,
                        0.025,
                        0.30,
                        25.0,
                        5.0,
                        18.0,
                        0.005,
                        0.01,
                        -0.02,
                        0.10,
                        0.20,
                        0.90,
                        0.0,
                        0.01,
                        0.15,
                        7,
                        0.95,
                        0.90,
                        0.05,
                        0.85,
                        true
                ),
                dataVersion
        );
    }

    private UsRelativePeerComparable peer(
            String ticker,
            String sector,
            String industry,
            String companyType,
            String sectorTemplate
    ) {
        return peer(ticker, sector, industry, companyType, sectorTemplate, new BigDecimal("0.10"));
    }

    private UsRelativePeerComparable peer(
            String ticker,
            String sector,
            String industry,
            String companyType,
            String sectorTemplate,
            BigDecimal revenueGrowthProxy
    ) {
        return new UsRelativePeerComparable(
                1L,
                ticker,
                ticker + " Corp",
                sector,
                industry,
                companyType,
                sectorTemplate,
                BigDecimal.TEN,
                new BigDecimal("1000000"),
                new BigDecimal("20"),
                new BigDecimal("4"),
                revenueGrowthProxy,
                new BigDecimal("0.08"),
                new BigDecimal("0.06"),
                new BigDecimal("100"),
                BigDecimal.ZERO
        );
    }

    private UsMarketSnapshotRecord marketSnapshot(BigDecimal marketCap) {
        return new UsMarketSnapshotRecord(
                Instant.parse("2026-03-31T00:00:00Z"),
                BigDecimal.TEN,
                marketCap,
                new BigDecimal("20"),
                new BigDecimal("4"),
                new BigDecimal("0.01")
        );
    }
}
