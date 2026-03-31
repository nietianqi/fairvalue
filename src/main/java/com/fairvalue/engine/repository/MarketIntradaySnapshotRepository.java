package com.fairvalue.engine.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public class MarketIntradaySnapshotRepository {
    private final JdbcClient jdbcClient;

    public MarketIntradaySnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(
            long securityId,
            Instant snapshotTime,
            BigDecimal lastPrice,
            Long volume,
            BigDecimal turnover,
            BigDecimal changePct,
            long sourceId
    ) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_intraday_snapshot (
                            security_id,
                            snapshot_time,
                            last_price,
                            volume,
                            turnover,
                            change_pct,
                            source_id
                        ) VALUES (
                            :securityId,
                            :snapshotTime,
                            :lastPrice,
                            :volume,
                            :turnover,
                            :changePct,
                            :sourceId
                        )
                        ON CONFLICT (security_id, snapshot_time, source_id)
                        DO UPDATE SET
                            last_price = EXCLUDED.last_price,
                            volume = EXCLUDED.volume,
                            turnover = EXCLUDED.turnover,
                            change_pct = EXCLUDED.change_pct
                        """)
                .param("securityId", securityId)
                .param("snapshotTime", snapshotTime == null ? null : java.sql.Timestamp.from(snapshotTime))
                .param("lastPrice", lastPrice)
                .param("volume", volume)
                .param("turnover", turnover)
                .param("changePct", changePct)
                .param("sourceId", sourceId)
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.market_intraday_snapshot
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }
}
