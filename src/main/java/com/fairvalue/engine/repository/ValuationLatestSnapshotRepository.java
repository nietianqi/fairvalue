package com.fairvalue.engine.repository;

import com.fairvalue.engine.api.dto.MarketRankingItem;
import com.fairvalue.engine.us.UsStoredValuationSnapshotRecord;
import com.fairvalue.engine.us.UsValuationLatestSnapshotRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ValuationLatestSnapshotRepository {
    private final JdbcClient jdbcClient;

    public ValuationLatestSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsert(UsValuationLatestSnapshotRecord record) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_latest_snapshot (
                            security_id,
                            market,
                            latest_run_id,
                            as_of_time,
                            current_price,
                            fair_value_low,
                            fair_value_mid,
                            fair_value_high,
                            upside_pct,
                            confidence_level,
                            margin_of_safety,
                            final_verdict,
                            implied_expectation,
                            value_trap_flag,
                            sector_template,
                            company_type,
                            quality_score,
                            data_quality_score,
                            data_version,
                            price_as_of,
                            price_freshness_days,
                            price_source_type,
                            rankable,
                            valuation_status,
                            exclusion_reason,
                            industry_match_source,
                            industry_match_confidence,
                            industry_fallback_used,
                            summary_json,
                            report_json,
                            source_attribution_json,
                            updated_at
                        ) VALUES (
                            :securityId,
                            :market,
                            :latestRunId,
                            :asOfTime,
                            :currentPrice,
                            :fairValueLow,
                            :fairValueMid,
                            :fairValueHigh,
                            :upsidePct,
                            :confidenceLevel,
                            :marginOfSafety,
                            :finalVerdict,
                            :impliedExpectation,
                            :valueTrapFlag,
                            :sectorTemplate,
                            :companyType,
                            :qualityScore,
                            :dataQualityScore,
                            :dataVersion,
                            :priceAsOf,
                            :priceFreshnessDays,
                            :priceSourceType,
                            :rankable,
                            :valuationStatus,
                            :exclusionReason,
                            :industryMatchSource,
                            :industryMatchConfidence,
                            :industryFallbackUsed,
                            CAST(:summaryJson AS jsonb),
                            CAST(:reportJson AS jsonb),
                            CAST(:sourceAttributionJson AS jsonb),
                            NOW()
                        )
                        ON CONFLICT (security_id)
                        DO UPDATE SET
                            market = EXCLUDED.market,
                            latest_run_id = EXCLUDED.latest_run_id,
                            as_of_time = EXCLUDED.as_of_time,
                            current_price = EXCLUDED.current_price,
                            fair_value_low = EXCLUDED.fair_value_low,
                            fair_value_mid = EXCLUDED.fair_value_mid,
                            fair_value_high = EXCLUDED.fair_value_high,
                            upside_pct = EXCLUDED.upside_pct,
                            confidence_level = EXCLUDED.confidence_level,
                            margin_of_safety = EXCLUDED.margin_of_safety,
                            final_verdict = EXCLUDED.final_verdict,
                            implied_expectation = EXCLUDED.implied_expectation,
                            value_trap_flag = EXCLUDED.value_trap_flag,
                            sector_template = EXCLUDED.sector_template,
                            company_type = EXCLUDED.company_type,
                            quality_score = EXCLUDED.quality_score,
                            data_quality_score = EXCLUDED.data_quality_score,
                            data_version = EXCLUDED.data_version,
                            price_as_of = EXCLUDED.price_as_of,
                            price_freshness_days = EXCLUDED.price_freshness_days,
                            price_source_type = EXCLUDED.price_source_type,
                            rankable = EXCLUDED.rankable,
                            valuation_status = EXCLUDED.valuation_status,
                            exclusion_reason = EXCLUDED.exclusion_reason,
                            industry_match_source = EXCLUDED.industry_match_source,
                            industry_match_confidence = EXCLUDED.industry_match_confidence,
                            industry_fallback_used = EXCLUDED.industry_fallback_used,
                            summary_json = EXCLUDED.summary_json,
                            report_json = EXCLUDED.report_json,
                            source_attribution_json = EXCLUDED.source_attribution_json,
                            updated_at = NOW()
                        """)
                .param("securityId", record.securityId())
                .param("market", record.market())
                .param("latestRunId", record.latestRunId())
                .param("asOfTime", Timestamp.from(record.asOfTime()))
                .param("currentPrice", record.currentPrice())
                .param("fairValueLow", record.fairValueLow())
                .param("fairValueMid", record.fairValueMid())
                .param("fairValueHigh", record.fairValueHigh())
                .param("upsidePct", record.upsidePct())
                .param("confidenceLevel", record.confidenceLevel())
                .param("marginOfSafety", record.marginOfSafety())
                .param("finalVerdict", record.finalVerdict())
                .param("impliedExpectation", record.impliedExpectation())
                .param("valueTrapFlag", record.valueTrapFlag())
                .param("sectorTemplate", record.sectorTemplate())
                .param("companyType", record.companyType())
                .param("qualityScore", record.qualityScore())
                .param("dataQualityScore", record.dataQualityScore())
                .param("dataVersion", record.dataVersion())
                .param("priceAsOf", record.priceAsOf())
                .param("priceFreshnessDays", record.priceFreshnessDays())
                .param("priceSourceType", record.priceSourceType())
                .param("rankable", record.rankable())
                .param("valuationStatus", record.valuationStatus())
                .param("exclusionReason", record.exclusionReason())
                .param("industryMatchSource", record.industryMatchSource())
                .param("industryMatchConfidence", record.industryMatchConfidence())
                .param("industryFallbackUsed", record.industryFallbackUsed())
                .param("summaryJson", emptyJson(record.summaryJson()))
                .param("reportJson", emptyJson(record.reportJson()))
                .param("sourceAttributionJson", emptyJson(record.sourceAttributionJson()))
                .update();
    }

    public Optional<UsStoredValuationSnapshotRecord> findByTicker(String ticker) {
        return jdbcClient.sql("""
                        SELECT vls.security_id,
                               sm.ticker,
                               COALESCE(vls.latest_run_id, 0) AS latest_run_id,
                               vls.as_of_time,
                               vls.current_price,
                               vls.fair_value_low,
                               vls.fair_value_mid,
                               vls.fair_value_high,
                               vls.upside_pct,
                               vls.confidence_level,
                               vls.margin_of_safety,
                               vls.final_verdict,
                               vls.implied_expectation,
                               vls.value_trap_flag,
                               vls.sector_template,
                               vls.company_type,
                               vls.quality_score,
                               vls.data_quality_score,
                               vls.data_version,
                               vls.price_as_of,
                               vls.price_freshness_days,
                               vls.price_source_type,
                               vls.rankable,
                               vls.valuation_status,
                               vls.exclusion_reason,
                               vls.industry_match_source,
                               vls.industry_match_confidence,
                               vls.industry_fallback_used,
                               vls.summary_json::text AS summary_json,
                               vls.report_json::text AS report_json,
                               vls.source_attribution_json::text AS source_attribution_json
                        FROM fairvalue.valuation_latest_snapshot vls
                        JOIN fairvalue.security_master sm
                          ON sm.id = vls.security_id
                        WHERE sm.ticker = :ticker
                        """)
                .param("ticker", ticker)
                .query(this::mapStoredSnapshot)
                .optional();
    }

    public List<UsStoredValuationSnapshotRecord> findAllByMarket(String market) {
        return jdbcClient.sql("""
                        SELECT vls.security_id,
                               sm.ticker,
                               COALESCE(vls.latest_run_id, 0) AS latest_run_id,
                               vls.as_of_time,
                               vls.current_price,
                               vls.fair_value_low,
                               vls.fair_value_mid,
                               vls.fair_value_high,
                               vls.upside_pct,
                               vls.confidence_level,
                               vls.margin_of_safety,
                               vls.final_verdict,
                               vls.implied_expectation,
                               vls.value_trap_flag,
                               vls.sector_template,
                               vls.company_type,
                               vls.quality_score,
                               vls.data_quality_score,
                               vls.data_version,
                               vls.price_as_of,
                               vls.price_freshness_days,
                               vls.price_source_type,
                               vls.rankable,
                               vls.valuation_status,
                               vls.exclusion_reason,
                               vls.industry_match_source,
                               vls.industry_match_confidence,
                               vls.industry_fallback_used,
                               vls.summary_json::text AS summary_json,
                               vls.report_json::text AS report_json,
                               vls.source_attribution_json::text AS source_attribution_json
                        FROM fairvalue.valuation_latest_snapshot vls
                        JOIN fairvalue.security_master sm
                          ON sm.id = vls.security_id
                        WHERE vls.market = :market
                        ORDER BY sm.ticker
                        """)
                .param("market", market)
                .query(this::mapStoredSnapshot)
                .list();
    }

    public long countByMarket(String market) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                        """)
                .param("market", market)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countStaleByMarket(String market, Instant staleBefore) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                          AND as_of_time < :staleBefore
                        """)
                .param("market", market)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countExcludedByReason(String market, String exclusionReason) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                          AND COALESCE(exclusion_reason, '') = :exclusionReason
                        """)
                .param("market", market)
                .param("exclusionReason", exclusionReason)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countIndustryFallbackExcludedByMarket(String market) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                          AND industry_fallback_used = TRUE
                        """)
                .param("market", market)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countLowConfidenceExcludedByMarket(String market, double minConfidence) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                          AND COALESCE(rankable, FALSE) = FALSE
                          AND COALESCE(confidence_level, 0) < :minConfidence
                        """)
                .param("market", market)
                .param("minConfidence", minConfidence)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countRankableByMarket(
            String market,
            Instant staleBefore,
            double minConfidence,
            double minMarketCap,
            double maxMarketCap
    ) {
        Long count = jdbcClient.sql("""
                        WITH latest_market AS (
                            SELECT DISTINCT ON (security_id)
                                   security_id,
                                   market_cap_vendor
                            FROM fairvalue.market_snapshot
                            ORDER BY security_id, snapshot_time DESC
                        )
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_latest_snapshot vls
                        LEFT JOIN latest_market lm
                          ON lm.security_id = vls.security_id
                        WHERE vls.market = :market
                          AND vls.rankable = TRUE
                          AND COALESCE(vls.industry_fallback_used, FALSE) = FALSE
                          AND vls.as_of_time >= :staleBefore
                          AND vls.confidence_level >= :minConfidence
                          AND vls.current_price IS NOT NULL
                          AND vls.fair_value_mid IS NOT NULL
                          AND COALESCE(lm.market_cap_vendor, 0) BETWEEN :minMarketCap AND :maxMarketCap
                        """)
                .param("market", market)
                .param("staleBefore", Timestamp.from(staleBefore))
                .param("minConfidence", minConfidence)
                .param("minMarketCap", minMarketCap)
                .param("maxMarketCap", maxMarketCap)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public LocalDate latestDataAsOf(String market) {
        return jdbcClient.sql("""
                        SELECT MAX(as_of_time)::date
                        FROM fairvalue.valuation_latest_snapshot
                        WHERE market = :market
                        """)
                .param("market", market)
                .query(LocalDate.class)
                .optional()
                .orElse(LocalDate.now());
    }

    public List<MarketRankingItem> findRankings(String market, boolean ascending, int limit, int offset) {
        String order = ascending ? "ASC" : "DESC";
        return jdbcClient.sql("""
                        WITH latest_market AS (
                            SELECT DISTINCT ON (security_id)
                                   security_id,
                                   pe_ttm_vendor,
                                   market_cap_vendor
                            FROM fairvalue.market_snapshot
                            ORDER BY security_id, snapshot_time DESC
                        ),
                        latest_intraday AS (
                            SELECT DISTINCT ON (security_id)
                                   security_id,
                                   change_pct,
                                   volume
                            FROM fairvalue.market_intraday_snapshot
                            ORDER BY security_id, snapshot_time DESC
                        )
                        SELECT sm.ticker,
                               sm.company_name,
                               COALESCE(sm.industry, sm.sector_template, sm.sector, sm.exchange) AS industry,
                               vls.current_price,
                               vls.fair_value_mid,
                               vls.fair_value_low,
                               vls.fair_value_high,
                               vls.upside_pct,
                               vls.confidence_level,
                               vls.final_verdict,
                               lm.pe_ttm_vendor,
                               NULL::numeric AS ev_ebitda,
                               lm.market_cap_vendor,
                               NULL::numeric AS growth_metric,
                               li.change_pct,
                               CASE WHEN li.volume IS NULL THEN NULL ELSE li.volume::numeric END AS activity_metric
                        FROM fairvalue.valuation_latest_snapshot vls
                        JOIN fairvalue.security_master sm
                          ON sm.id = vls.security_id
                        LEFT JOIN latest_market lm
                          ON lm.security_id = vls.security_id
                        LEFT JOIN latest_intraday li
                          ON li.security_id = vls.security_id
                        WHERE vls.market = :market
                        ORDER BY vls.upside_pct __ORDER__, sm.ticker
                        LIMIT :limit OFFSET :offset
                        """.replace("__ORDER__", order))
                .param("market", market)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new MarketRankingItem(
                        rs.getString("ticker"),
                        rs.getString("company_name"),
                        rs.getString("industry"),
                        nullableDouble(rs, "current_price"),
                        nullableDouble(rs, "fair_value_mid"),
                        nullableDouble(rs, "fair_value_low"),
                        nullableDouble(rs, "fair_value_high"),
                        nullableDouble(rs, "upside_pct"),
                        nullableDouble(rs, "confidence_level"),
                        rs.getString("final_verdict"),
                        nullableDouble(rs, "pe_ttm_vendor"),
                        nullableDouble(rs, "ev_ebitda"),
                        nullableDouble(rs, "market_cap_vendor"),
                        nullableDouble(rs, "growth_metric"),
                        nullableDouble(rs, "change_pct"),
                        nullableDouble(rs, "activity_metric")
                ))
                .list();
    }

    public List<MarketRankingItem> findStrictRankings(
            String market,
            String rankingType,
            Instant staleBefore,
            double minConfidence,
            double minMarketCap,
            double maxMarketCap,
            int limit,
            int offset
    ) {
        String orderBy = switch (rankingType == null ? "" : rankingType.toLowerCase()) {
            case "overvalued" -> "vls.upside_pct ASC, vls.confidence_level DESC, sm.ticker";
            case "quality" -> "vls.quality_score DESC NULLS LAST, vls.confidence_level DESC, sm.ticker";
            case "value_trap" -> "vls.value_trap_flag DESC, vls.upside_pct ASC, vls.confidence_level DESC, sm.ticker";
            default -> "vls.upside_pct DESC, vls.confidence_level DESC, sm.ticker";
        };
        return jdbcClient.sql("""
                        WITH latest_market AS (
                            SELECT DISTINCT ON (security_id)
                                   security_id,
                                   pe_ttm_vendor,
                                   market_cap_vendor
                            FROM fairvalue.market_snapshot
                            ORDER BY security_id, snapshot_time DESC
                        ),
                        latest_intraday AS (
                            SELECT DISTINCT ON (security_id)
                                   security_id,
                                   change_pct,
                                   volume
                            FROM fairvalue.market_intraday_snapshot
                            ORDER BY security_id, snapshot_time DESC
                        )
                        SELECT sm.ticker,
                               sm.company_name,
                               COALESCE(sm.industry, sm.sector_template, sm.sector, sm.exchange) AS industry,
                               vls.current_price,
                               vls.fair_value_mid,
                               vls.fair_value_low,
                               vls.fair_value_high,
                               vls.upside_pct,
                               vls.confidence_level,
                               vls.final_verdict,
                               lm.pe_ttm_vendor,
                               NULL::numeric AS ev_ebitda,
                               lm.market_cap_vendor,
                               vls.quality_score AS growth_metric,
                               li.change_pct,
                               CASE WHEN li.volume IS NULL THEN NULL ELSE li.volume::numeric END AS activity_metric
                        FROM fairvalue.valuation_latest_snapshot vls
                        JOIN fairvalue.security_master sm
                          ON sm.id = vls.security_id
                        LEFT JOIN latest_market lm
                          ON lm.security_id = vls.security_id
                        LEFT JOIN latest_intraday li
                          ON li.security_id = vls.security_id
                        WHERE vls.market = :market
                          AND vls.rankable = TRUE
                          AND COALESCE(vls.industry_fallback_used, FALSE) = FALSE
                          AND vls.as_of_time >= :staleBefore
                          AND vls.confidence_level >= :minConfidence
                          AND vls.current_price IS NOT NULL
                          AND vls.fair_value_mid IS NOT NULL
                          AND COALESCE(lm.market_cap_vendor, 0) BETWEEN :minMarketCap AND :maxMarketCap
                        ORDER BY __ORDER_BY__
                        LIMIT :limit OFFSET :offset
                        """.replace("__ORDER_BY__", orderBy))
                .param("market", market)
                .param("staleBefore", Timestamp.from(staleBefore))
                .param("minConfidence", minConfidence)
                .param("minMarketCap", minMarketCap)
                .param("maxMarketCap", maxMarketCap)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new MarketRankingItem(
                        rs.getString("ticker"),
                        rs.getString("company_name"),
                        rs.getString("industry"),
                        nullableDouble(rs, "current_price"),
                        nullableDouble(rs, "fair_value_mid"),
                        nullableDouble(rs, "fair_value_low"),
                        nullableDouble(rs, "fair_value_high"),
                        nullableDouble(rs, "upside_pct"),
                        nullableDouble(rs, "confidence_level"),
                        rs.getString("final_verdict"),
                        nullableDouble(rs, "pe_ttm_vendor"),
                        nullableDouble(rs, "ev_ebitda"),
                        nullableDouble(rs, "market_cap_vendor"),
                        nullableDouble(rs, "growth_metric"),
                        nullableDouble(rs, "change_pct"),
                        nullableDouble(rs, "activity_metric")
                ))
                .list();
    }

    private Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column) == null ? null : rs.getDouble(column);
    }

    private String emptyJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private UsStoredValuationSnapshotRecord mapStoredSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UsStoredValuationSnapshotRecord(
                rs.getLong("security_id"),
                rs.getString("ticker"),
                rs.getLong("latest_run_id"),
                rs.getTimestamp("as_of_time").toInstant(),
                rs.getDouble("current_price"),
                rs.getDouble("fair_value_low"),
                rs.getDouble("fair_value_mid"),
                rs.getDouble("fair_value_high"),
                rs.getDouble("upside_pct"),
                rs.getDouble("confidence_level"),
                rs.getDouble("margin_of_safety"),
                rs.getString("final_verdict"),
                rs.getString("implied_expectation"),
                rs.getBoolean("value_trap_flag"),
                rs.getString("sector_template"),
                rs.getString("company_type"),
                rs.getObject("quality_score") == null ? null : rs.getDouble("quality_score"),
                rs.getObject("data_quality_score") == null ? null : rs.getDouble("data_quality_score"),
                rs.getString("data_version"),
                rs.getObject("price_as_of", LocalDate.class),
                rs.getObject("price_freshness_days") == null ? null : rs.getInt("price_freshness_days"),
                rs.getString("price_source_type"),
                rs.getBoolean("rankable"),
                rs.getString("valuation_status"),
                rs.getString("exclusion_reason"),
                rs.getString("industry_match_source"),
                rs.getObject("industry_match_confidence") == null ? null : rs.getDouble("industry_match_confidence"),
                rs.getBoolean("industry_fallback_used"),
                rs.getString("summary_json"),
                rs.getString("report_json"),
                rs.getString("source_attribution_json")
        );
    }
}
