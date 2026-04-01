package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.us.UsRelativePeerComparable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class SecurityMasterRepository {
    private final JdbcClient jdbcClient;

    public SecurityMasterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UsSecurityMaster> findByTicker(String ticker) {
        return jdbcClient.sql("""
                        SELECT id,
                               ticker,
                               symbol_full,
                               company_name,
                               exchange,
                               currency,
                               sector,
                               industry,
                               subindustry,
                               company_type,
                               sector_template,
                               country,
                               is_active
                        FROM fairvalue.security_master
                        WHERE ticker = :ticker
                        """)
                .param("ticker", ticker)
                .query(this::mapRow)
                .optional();
    }

    public Optional<UsSecurityMaster> findById(long id) {
        return jdbcClient.sql("""
                        SELECT id,
                               ticker,
                               symbol_full,
                               company_name,
                               exchange,
                               currency,
                               sector,
                               industry,
                               subindustry,
                               company_type,
                               sector_template,
                               country,
                               is_active
                        FROM fairvalue.security_master
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    public long insert(UsSecurityMaster security) {
        return jdbcClient.sql("""
                        INSERT INTO fairvalue.security_master (
                            ticker,
                            symbol_full,
                            company_name,
                            exchange,
                            currency,
                            sector,
                            industry,
                            subindustry,
                            company_type,
                            sector_template,
                            country,
                            is_active,
                            updated_at
                        ) VALUES (
                            :ticker,
                            :symbolFull,
                            :companyName,
                            :exchange,
                            :currency,
                            :sector,
                            :industry,
                            :subindustry,
                            :companyType,
                            :sectorTemplate,
                            :country,
                            :active,
                            NOW()
                        )
                        RETURNING id
                        """)
                .param("ticker", security.ticker())
                .param("symbolFull", security.symbolFull())
                .param("companyName", security.companyName())
                .param("exchange", security.exchange())
                .param("currency", security.currency())
                .param("sector", security.sector())
                .param("industry", security.industry())
                .param("subindustry", security.subindustry())
                .param("companyType", security.companyType())
                .param("sectorTemplate", security.sectorTemplate())
                .param("country", security.country())
                .param("active", security.active())
                .query(Long.class)
                .single();
    }

    public void update(long id, UsSecurityMaster security) {
        jdbcClient.sql("""
                        UPDATE fairvalue.security_master
                        SET ticker = :ticker,
                            symbol_full = :symbolFull,
                            company_name = :companyName,
                            exchange = :exchange,
                            currency = :currency,
                            sector = COALESCE(:sector, sector),
                            industry = COALESCE(:industry, industry),
                            subindustry = COALESCE(:subindustry, subindustry),
                            company_type = COALESCE(:companyType, company_type),
                            sector_template = COALESCE(:sectorTemplate, sector_template),
                            country = :country,
                            is_active = :active,
                            updated_at = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("ticker", security.ticker())
                .param("symbolFull", security.symbolFull())
                .param("companyName", security.companyName())
                .param("exchange", security.exchange())
                .param("currency", security.currency())
                .param("sector", security.sector())
                .param("industry", security.industry())
                .param("subindustry", security.subindustry())
                .param("companyType", security.companyType())
                .param("sectorTemplate", security.sectorTemplate())
                .param("country", security.country())
                .param("active", security.active())
                .update();
    }

    public List<UsRelativePeerComparable> findRelativePeers(
            long securityId,
            String sector,
            String industry,
            String companyType,
            String sectorTemplate,
            int limit,
            double minFcfMargin,
            double minRoic
    ) {
        return jdbcClient.sql("""
                        SELECT sm.id,
                               sm.ticker,
                               sm.company_name,
                               sm.sector,
                               sm.industry,
                               sm.company_type,
                               sm.sector_template,
                               ms.last_price,
                               ms.market_cap_vendor,
                               ms.pe_ttm_vendor,
                               ms.pb_vendor,
                               CASE
                                   WHEN fr_prev.revenue IS NOT NULL AND fr_prev.revenue > 0 AND fr.revenue IS NOT NULL
                                       THEN (fr.revenue / fr_prev.revenue) - 1
                                   ELSE NULL
                               END AS revenue_growth_proxy,
                               fd.roic,
                               fd.fcf_margin,
                               fs.ebitda,
                               fs.net_debt
                        FROM fairvalue.security_master sm
                        LEFT JOIN LATERAL (
                            SELECT last_price,
                                   market_cap_vendor,
                                   pe_ttm_vendor,
                                   pb_vendor
                            FROM fairvalue.market_snapshot
                            WHERE security_id = sm.id
                            ORDER BY snapshot_time DESC
                            LIMIT 1
                        ) ms ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT period_type,
                                   period_end,
                                   revenue
                            FROM fairvalue.financial_standardized
                            WHERE security_id = sm.id
                              AND revenue IS NOT NULL
                              AND period_type IN ('TTM', 'FY')
                            ORDER BY CASE period_type
                                         WHEN 'TTM' THEN 1
                                         WHEN 'FY' THEN 2
                                         ELSE 3
                                     END,
                                     period_end DESC
                            LIMIT 1
                        ) fr ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT revenue
                            FROM fairvalue.financial_standardized
                            WHERE security_id = sm.id
                              AND revenue IS NOT NULL
                              AND period_type = fr.period_type
                              AND period_end < fr.period_end
                            ORDER BY period_end DESC
                            LIMIT 1
                        ) fr_prev ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT roic,
                                   fcf_margin
                            FROM fairvalue.financial_derived_metrics
                            WHERE security_id = sm.id
                            ORDER BY period_end DESC, period_type
                            LIMIT 1
                        ) fd ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT ebitda,
                                   net_debt
                            FROM fairvalue.financial_standardized
                            WHERE security_id = sm.id
                            ORDER BY period_end DESC,
                                     CASE period_type
                                         WHEN 'TTM' THEN 1
                                         WHEN 'FY' THEN 2
                                         ELSE 3
                                     END
                            LIMIT 1
                        ) fs ON TRUE
                        WHERE sm.id <> :securityId
                          AND sm.country = 'US'
                          AND sm.is_active = TRUE
                          AND ms.last_price IS NOT NULL
                          AND (fs.ebitda IS NULL OR fs.ebitda > 0)
                          AND (:minFcfMargin < 0 OR (fd.fcf_margin IS NOT NULL AND fd.fcf_margin >= :minFcfMargin))
                          AND (:minRoic < 0 OR (fd.roic IS NOT NULL AND fd.roic >= :minRoic))
                          AND (
                                (:industry IS NOT NULL AND sm.industry = :industry)
                             OR (:sectorTemplate IS NOT NULL AND sm.sector_template = :sectorTemplate)
                             OR (:companyType IS NOT NULL AND sm.company_type = :companyType)
                             OR (:sector IS NOT NULL AND sm.sector = :sector)
                          )
                        ORDER BY CASE
                                     WHEN :industry IS NOT NULL AND sm.industry = :industry THEN 1
                                     WHEN :sectorTemplate IS NOT NULL AND sm.sector_template = :sectorTemplate THEN 2
                                     WHEN :companyType IS NOT NULL AND sm.company_type = :companyType THEN 3
                                     WHEN :sector IS NOT NULL AND sm.sector = :sector THEN 4
                                     ELSE 5
                                 END,
                                 ms.market_cap_vendor DESC NULLS LAST,
                                 sm.ticker
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("sector", blankToNull(sector))
                .param("industry", blankToNull(industry))
                .param("companyType", blankToNull(companyType))
                .param("sectorTemplate", blankToNull(sectorTemplate))
                .param("limit", limit)
                .param("minFcfMargin", minFcfMargin)
                .param("minRoic", minRoic)
                .query(this::mapRelativePeer)
                .list();
    }

    private UsSecurityMaster mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UsSecurityMaster(
                rs.getLong("id"),
                rs.getString("ticker"),
                rs.getString("symbol_full"),
                rs.getString("company_name"),
                rs.getString("exchange"),
                rs.getString("currency"),
                rs.getString("sector"),
                rs.getString("industry"),
                rs.getString("subindustry"),
                rs.getString("company_type"),
                rs.getString("sector_template"),
                rs.getString("country"),
                rs.getBoolean("is_active")
        );
    }

    private UsRelativePeerComparable mapRelativePeer(ResultSet rs, int rowNum) throws SQLException {
        return new UsRelativePeerComparable(
                rs.getLong("id"),
                rs.getString("ticker"),
                rs.getString("company_name"),
                rs.getString("sector"),
                rs.getString("industry"),
                rs.getString("company_type"),
                rs.getString("sector_template"),
                rs.getBigDecimal("last_price"),
                rs.getBigDecimal("market_cap_vendor"),
                rs.getBigDecimal("pe_ttm_vendor"),
                rs.getBigDecimal("pb_vendor"),
                rs.getBigDecimal("revenue_growth_proxy"),
                rs.getBigDecimal("roic"),
                rs.getBigDecimal("fcf_margin"),
                rs.getBigDecimal("ebitda"),
                rs.getBigDecimal("net_debt")
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
