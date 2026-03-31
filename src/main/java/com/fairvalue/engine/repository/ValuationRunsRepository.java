package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsValuationRunRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ValuationRunsRepository {
    private final JdbcClient jdbcClient;

    public ValuationRunsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(UsValuationRunRecord record) {
        Long id = jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_runs (
                            security_id,
                            valuation_date,
                            run_mode,
                            current_price,
                            fair_value_low,
                            fair_value_mid,
                            fair_value_high,
                            blended_intrinsic_value,
                            confidence_level,
                            margin_of_safety,
                            implied_expectation_label,
                            sector_template,
                            company_type,
                            weighted_value,
                            final_verdict,
                            buy_zone_low,
                            buy_zone_high,
                            hold_zone_low,
                            hold_zone_high,
                            avoid_zone_low,
                            avoid_zone_high,
                            report_json
                        ) VALUES (
                            :securityId,
                            :valuationDate,
                            :runMode,
                            :currentPrice,
                            :fairValueLow,
                            :fairValueMid,
                            :fairValueHigh,
                            :blendedIntrinsicValue,
                            :confidenceLevel,
                            :marginOfSafety,
                            :impliedExpectationLabel,
                            :sectorTemplate,
                            :companyType,
                            :weightedValue,
                            :finalVerdict,
                            :buyZoneLow,
                            :buyZoneHigh,
                            :holdZoneLow,
                            :holdZoneHigh,
                            :avoidZoneLow,
                            :avoidZoneHigh,
                            CAST(:reportJson AS jsonb)
                        )
                        RETURNING id
                        """)
                .param("securityId", record.securityId())
                .param("valuationDate", java.sql.Timestamp.from(record.valuationDate()))
                .param("runMode", record.runMode())
                .param("currentPrice", record.currentPrice())
                .param("fairValueLow", record.fairValueLow())
                .param("fairValueMid", record.fairValueMid())
                .param("fairValueHigh", record.fairValueHigh())
                .param("blendedIntrinsicValue", record.blendedIntrinsicValue())
                .param("confidenceLevel", record.confidenceLevel())
                .param("marginOfSafety", record.marginOfSafety())
                .param("impliedExpectationLabel", record.impliedExpectationLabel())
                .param("sectorTemplate", record.sectorTemplate())
                .param("companyType", record.companyType())
                .param("weightedValue", record.weightedValue())
                .param("finalVerdict", record.finalVerdict())
                .param("buyZoneLow", record.buyZoneLow())
                .param("buyZoneHigh", record.buyZoneHigh())
                .param("holdZoneLow", record.holdZoneLow())
                .param("holdZoneHigh", record.holdZoneHigh())
                .param("avoidZoneLow", record.avoidZoneLow())
                .param("avoidZoneHigh", record.avoidZoneHigh())
                .param("reportJson", emptyJson(record.reportJson()))
                .query(Long.class)
                .single();
        return id == null ? 0L : id;
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_runs
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
