package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsMarketSnapshotRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public class MarketSnapshotRepository {
    private final JdbcClient jdbcClient;

    public MarketSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(
            long securityId,
            Instant snapshotTime,
            BigDecimal lastPrice,
            BigDecimal prevClose,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            Long volume,
            BigDecimal turnover,
            BigDecimal marketCapVendor,
            BigDecimal peTtmVendor,
            BigDecimal pbVendor,
            BigDecimal dividendYieldVendor,
            BigDecimal epsTtmVendor,
            BigDecimal bpsVendor,
            BigDecimal floatSharesVendor,
            BigDecimal totalSharesVendor,
            long sourceId
    ) {
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
                            :lastPrice,
                            :prevClose,
                            :openPrice,
                            :highPrice,
                            :lowPrice,
                            :volume,
                            :turnover,
                            :marketCapVendor,
                            :peTtmVendor,
                            :pbVendor,
                            :dividendYieldVendor,
                            :epsTtmVendor,
                            :bpsVendor,
                            :floatSharesVendor,
                            :totalSharesVendor,
                            :sourceId
                        )
                        ON CONFLICT (security_id, snapshot_time, source_id)
                        DO UPDATE SET
                            last_price = EXCLUDED.last_price,
                            prev_close = EXCLUDED.prev_close,
                            open_price = EXCLUDED.open_price,
                            high_price = EXCLUDED.high_price,
                            low_price = EXCLUDED.low_price,
                            volume = EXCLUDED.volume,
                            turnover = EXCLUDED.turnover,
                            market_cap_vendor = EXCLUDED.market_cap_vendor,
                            pe_ttm_vendor = EXCLUDED.pe_ttm_vendor,
                            pb_vendor = EXCLUDED.pb_vendor,
                            dividend_yield_vendor = EXCLUDED.dividend_yield_vendor,
                            eps_ttm_vendor = EXCLUDED.eps_ttm_vendor,
                            bps_vendor = EXCLUDED.bps_vendor,
                            float_shares_vendor = EXCLUDED.float_shares_vendor,
                            total_shares_vendor = EXCLUDED.total_shares_vendor
                        """)
                .param("securityId", securityId)
                .param("snapshotTime", snapshotTime == null ? null : java.sql.Timestamp.from(snapshotTime))
                .param("lastPrice", lastPrice)
                .param("prevClose", prevClose)
                .param("openPrice", openPrice)
                .param("highPrice", highPrice)
                .param("lowPrice", lowPrice)
                .param("volume", volume)
                .param("turnover", turnover)
                .param("marketCapVendor", marketCapVendor)
                .param("peTtmVendor", peTtmVendor)
                .param("pbVendor", pbVendor)
                .param("dividendYieldVendor", dividendYieldVendor)
                .param("epsTtmVendor", epsTtmVendor)
                .param("bpsVendor", bpsVendor)
                .param("floatSharesVendor", floatSharesVendor)
                .param("totalSharesVendor", totalSharesVendor)
                .param("sourceId", sourceId)
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.market_snapshot
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsMarketSnapshotRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT snapshot_time,
                               last_price,
                               market_cap_vendor,
                               pe_ttm_vendor,
                               pb_vendor,
                               dividend_yield_vendor
                        FROM fairvalue.market_snapshot
                        WHERE security_id = :securityId
                        ORDER BY snapshot_time DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list();
    }

    private RowMapper<UsMarketSnapshotRecord> rowMapper() {
        return (rs, rowNum) -> new UsMarketSnapshotRecord(
                rs.getTimestamp("snapshot_time").toInstant(),
                rs.getBigDecimal("last_price"),
                rs.getBigDecimal("market_cap_vendor"),
                rs.getBigDecimal("pe_ttm_vendor"),
                rs.getBigDecimal("pb_vendor"),
                rs.getBigDecimal("dividend_yield_vendor")
        );
    }
}
