package com.fairvalue.engine.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SecurityIdentifierMapRepository {
    private static final String SEC_EDGAR = "sec_edgar";
    private static final String LONGBRIDGE_API = "longbridge_api";

    private final JdbcClient jdbcClient;

    public SecurityIdentifierMapRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findSecurityIdByCik(String cik) {
        return jdbcClient.sql("""
                        SELECT security_id
                        FROM fairvalue.security_identifier_map
                        WHERE cik = :cik
                        ORDER BY is_primary DESC, updated_at DESC
                        LIMIT 1
                        """)
                .param("cik", cik)
                .query(Long.class)
                .optional();
    }

    public Optional<Long> findSecurityIdBySourceSymbol(String sourceName, String sourceSymbol) {
        return jdbcClient.sql("""
                        SELECT security_id
                        FROM fairvalue.security_identifier_map
                        WHERE source_name = :sourceName
                          AND source_symbol = :sourceSymbol
                        ORDER BY is_primary DESC, updated_at DESC
                        LIMIT 1
                        """)
                .param("sourceName", sourceName)
                .param("sourceSymbol", sourceSymbol)
                .query(Long.class)
                .optional();
    }

    public void upsertSecEdgarMapping(long securityId, String ticker, String cik) {
        int updated = jdbcClient.sql("""
                        UPDATE fairvalue.security_identifier_map
                        SET security_id = :securityId,
                            source_symbol = :sourceSymbol,
                            cik = :cik,
                            exchange_code = 'US',
                            is_primary = TRUE,
                            updated_at = NOW()
                        WHERE source_name = :sourceName
                          AND (security_id = :securityId OR cik = :cik OR source_symbol = :sourceSymbol)
                        """)
                .param("securityId", securityId)
                .param("sourceSymbol", ticker)
                .param("cik", cik)
                .param("sourceName", SEC_EDGAR)
                .update();

        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.security_identifier_map (
                                security_id,
                                source_name,
                                source_symbol,
                                cik,
                                exchange_code,
                                is_primary,
                                updated_at
                            ) VALUES (
                                :securityId,
                                :sourceName,
                                :sourceSymbol,
                                :cik,
                                'US',
                                TRUE,
                                NOW()
                            )
                            ON CONFLICT (source_name, source_symbol) WHERE source_symbol IS NOT NULL
                            DO UPDATE SET
                                security_id = EXCLUDED.security_id,
                                cik = EXCLUDED.cik,
                                exchange_code = EXCLUDED.exchange_code,
                                is_primary = EXCLUDED.is_primary,
                                updated_at = NOW()
                            """)
                    .param("securityId", securityId)
                    .param("sourceName", SEC_EDGAR)
                    .param("sourceSymbol", ticker)
                    .param("cik", cik)
                    .update();
        }
    }

    public void upsertLongbridgeMapping(long securityId, String symbolFull, String exchangeCode) {
        int updated = jdbcClient.sql("""
                        UPDATE fairvalue.security_identifier_map
                        SET security_id = :securityId,
                            source_symbol = :sourceSymbol,
                            exchange_code = :exchangeCode,
                            is_primary = TRUE,
                            updated_at = NOW()
                        WHERE source_name = :sourceName
                          AND (security_id = :securityId OR source_symbol = :sourceSymbol)
                        """)
                .param("securityId", securityId)
                .param("sourceSymbol", symbolFull)
                .param("exchangeCode", exchangeCode)
                .param("sourceName", LONGBRIDGE_API)
                .update();

        if (updated == 0) {
            jdbcClient.sql("""
                            INSERT INTO fairvalue.security_identifier_map (
                                security_id,
                                source_name,
                                source_symbol,
                                exchange_code,
                                is_primary,
                                updated_at
                            ) VALUES (
                                :securityId,
                                :sourceName,
                                :sourceSymbol,
                                :exchangeCode,
                                TRUE,
                                NOW()
                            )
                            ON CONFLICT (source_name, source_symbol) WHERE source_symbol IS NOT NULL
                            DO UPDATE SET
                                security_id = EXCLUDED.security_id,
                                exchange_code = EXCLUDED.exchange_code,
                                is_primary = EXCLUDED.is_primary,
                                updated_at = NOW()
                            """)
                    .param("securityId", securityId)
                    .param("sourceName", LONGBRIDGE_API)
                    .param("sourceSymbol", symbolFull)
                    .param("exchangeCode", exchangeCode)
                    .update();
        }
    }
}
