package com.fairvalue.engine.us;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "market-data.us.live.enabled=true",
        "market-data.us.live.universe-sync-on-startup=false"
})
class UsFinancialQualityScoringServiceTest {
    @Autowired
    private UsFinancialQualityScoringService usFinancialQualityScoringService;

    @Autowired
    private UsSecurityMasterService usSecurityMasterService;

    @Autowired
    private JdbcClient jdbcClient;

    private long aaplSecurityId;

    @BeforeEach
    void setUp() {
        aaplSecurityId = usSecurityMasterService.resolveSecurityId("AAPL").orElseThrow();
        jdbcClient.sql("DELETE FROM fairvalue.financial_quality_scores WHERE security_id = :securityId")
                .param("securityId", aaplSecurityId)
                .update();
    }

    @Test
    void shouldDeduplicateScoresWhenMultipleQuarterRowsSharePeriodEnd() {
        LocalDate periodEnd = LocalDate.of(2025, 9, 27);

        List<UsFinancialStandardizedRecord> standardizedRows = List.of(
                new UsFinancialStandardizedRecord(
                        aaplSecurityId, "Q", 2025, "Q1", null, periodEnd,
                        null, null, null, null, null, null, null, null,
                        new BigDecimal("35934"), new BigDecimal("12350"), new BigDecimal("78328"), new BigDecimal("90678"),
                        new BigDecimal("73733"), new BigDecimal("359241"), new BigDecimal("285508"),
                        null, null, null, null, null, null, null, null, null,
                        new BigDecimal("54744"), 7395L,
                        "{\"missing_revenue\":true}"
                ),
                new UsFinancialStandardizedRecord(
                        aaplSecurityId, "Q", 2025, "Q4", LocalDate.of(2025, 6, 29), periodEnd,
                        new BigDecimal("102466"), new BigDecimal("48341"), new BigDecimal("31856"), new BigDecimal("32427"),
                        new BigDecimal("27466"), new BigDecimal("29728"), new BigDecimal("3242"), new BigDecimal("26486"),
                        new BigDecimal("35934"), new BigDecimal("12350"), new BigDecimal("78328"), new BigDecimal("90678"),
                        new BigDecimal("73733"), new BigDecimal("359241"), new BigDecimal("285508"),
                        new BigDecimal("15004697"), new BigDecimal("14948500"), new BigDecimal("3183"), new BigDecimal("12490"),
                        null, null, null, null, new BigDecimal("0.1561"),
                        new BigDecimal("54744"), 7406L,
                        "{\"derived_q4_from_fy\":true}"
                )
        );

        List<UsFinancialDerivedMetricRecord> derivedRows = List.of(
                new UsFinancialDerivedMetricRecord(
                        aaplSecurityId, "Q", 2025, "Q1", periodEnd,
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null
                ),
                new UsFinancialDerivedMetricRecord(
                        aaplSecurityId, "Q", 2025, "Q4", periodEnd,
                        new BigDecimal("0.471776"), new BigDecimal("0.316466"), new BigDecimal("0.258486"),
                        new BigDecimal("0.372506"), new BigDecimal("0.194124"), new BigDecimal("0.076456"),
                        new BigDecimal("0.964320"), new BigDecimal("-0.006297"), new BigDecimal("1.718483"),
                        null, null, new BigDecimal("4.913995"), new BigDecimal("1.830493"),
                        new BigDecimal("20470"), null
                )
        );

        int rowCount = usFinancialQualityScoringService.refreshForSecurity(aaplSecurityId, standardizedRows, derivedRows);

        Long storedCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.financial_quality_scores
                        WHERE security_id = :securityId
                          AND period_type = 'Q'
                          AND period_end = :periodEnd
                        """)
                .param("securityId", aaplSecurityId)
                .param("periodEnd", periodEnd)
                .query(Long.class)
                .single();
        BigDecimal totalQualityScore = jdbcClient.sql("""
                        SELECT total_quality_score
                        FROM fairvalue.financial_quality_scores
                        WHERE security_id = :securityId
                          AND period_type = 'Q'
                          AND period_end = :periodEnd
                        """)
                .param("securityId", aaplSecurityId)
                .param("periodEnd", periodEnd)
                .query(BigDecimal.class)
                .single();

        assertThat(rowCount).isEqualTo(1);
        assertThat(storedCount).isEqualTo(1L);
        assertThat(totalQualityScore).isNotNull();
    }
}
