package com.fairvalue.engine.repository;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;

@Repository
public class MarketDataRawRepository {
    private final JdbcClient jdbcClient;

    public MarketDataRawRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(long securityId, long sourceId, String dataType, LocalDate tradeDate, Instant tradeTs, String rawJson) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_data_raw (
                            security_id,
                            source_id,
                            data_type,
                            trade_date,
                            trade_ts,
                            raw_json
                        ) VALUES (
                            :securityId,
                            :sourceId,
                            :dataType,
                            :tradeDate,
                            :tradeTs,
                            CAST(:rawJson AS jsonb)
                        )
                        """)
                .param("securityId", securityId)
                .param("sourceId", sourceId)
                .param("dataType", dataType)
                .param("tradeDate", tradeDate)
                .param("tradeTs", tradeTs == null ? null : java.sql.Timestamp.from(tradeTs))
                .param("rawJson", rawJson == null || rawJson.isBlank() ? "{}" : rawJson)
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.market_data_raw
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }
}
