package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsMarketPriceDailyRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class MarketPriceDailyRepository {
    private final JdbcClient jdbcClient;

    public MarketPriceDailyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsert(
            long securityId,
            LocalDate tradeDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume,
            BigDecimal turnover,
            BigDecimal adjClose,
            BigDecimal dividendAdjustmentFactor,
            BigDecimal splitAdjustmentFactor,
            long sourceId
    ) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.market_price_daily (
                            security_id,
                            trade_date,
                            open,
                            high,
                            low,
                            close,
                            volume,
                            turnover,
                            adj_close,
                            dividend_adjustment_factor,
                            split_adjustment_factor,
                            source_id
                        ) VALUES (
                            :securityId,
                            :tradeDate,
                            :open,
                            :high,
                            :low,
                            :close,
                            :volume,
                            :turnover,
                            :adjClose,
                            :dividendAdjustmentFactor,
                            :splitAdjustmentFactor,
                            :sourceId
                        )
                        ON CONFLICT (security_id, trade_date, source_id)
                        DO UPDATE SET
                            open = EXCLUDED.open,
                            high = EXCLUDED.high,
                            low = EXCLUDED.low,
                            close = EXCLUDED.close,
                            volume = EXCLUDED.volume,
                            turnover = EXCLUDED.turnover,
                            adj_close = EXCLUDED.adj_close,
                            dividend_adjustment_factor = EXCLUDED.dividend_adjustment_factor,
                            split_adjustment_factor = EXCLUDED.split_adjustment_factor
                        """)
                .param("securityId", securityId)
                .param("tradeDate", tradeDate)
                .param("open", open)
                .param("high", high)
                .param("low", low)
                .param("close", close)
                .param("volume", volume)
                .param("turnover", turnover)
                .param("adjClose", adjClose)
                .param("dividendAdjustmentFactor", dividendAdjustmentFactor)
                .param("splitAdjustmentFactor", splitAdjustmentFactor)
                .param("sourceId", sourceId)
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.market_price_daily
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public List<UsMarketPriceDailyRecord> findLatestBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT trade_date,
                               close
                        FROM fairvalue.market_price_daily
                        WHERE security_id = :securityId
                        ORDER BY trade_date DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list();
    }

    public List<UsMarketPriceDailyRecord> findRecentHistoryBySecurityId(long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT trade_date,
                               close
                        FROM fairvalue.market_price_daily
                        WHERE security_id = :securityId
                        ORDER BY trade_date DESC
                        LIMIT :limit
                        """)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(rowMapper())
                .list()
                .stream()
                .sorted(java.util.Comparator.comparing(UsMarketPriceDailyRecord::tradeDate))
                .toList();
    }

    private RowMapper<UsMarketPriceDailyRecord> rowMapper() {
        return (rs, rowNum) -> new UsMarketPriceDailyRecord(
                rs.getObject("trade_date", LocalDate.class),
                rs.getBigDecimal("close")
        );
    }
}
