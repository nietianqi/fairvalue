package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsSecurityMaster;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
}
