package com.fairvalue.engine.repository;

import com.fairvalue.engine.platform.ApiUsageRouteRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class ApiUsageDailyRepository {
    private final JdbcClient jdbcClient;

    public ApiUsageDailyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void incrementUsage(long clientId, LocalDate usageDate, String routeKey, String httpMethod, String statusBucket, Instant seenAt) {
        jdbcClient.sql("""
                        INSERT INTO fairvalue.api_usage_daily (
                            client_id,
                            usage_date,
                            route_key,
                            http_method,
                            status_bucket,
                            request_count,
                            last_seen_at
                        ) VALUES (
                            :clientId,
                            :usageDate,
                            :routeKey,
                            :httpMethod,
                            :statusBucket,
                            1,
                            :seenAt
                        )
                        ON CONFLICT (client_id, usage_date, route_key, http_method, status_bucket)
                        DO UPDATE
                        SET request_count = fairvalue.api_usage_daily.request_count + 1,
                            last_seen_at = EXCLUDED.last_seen_at,
                            updated_at = NOW()
                        """)
                .param("clientId", clientId)
                .param("usageDate", Date.valueOf(usageDate))
                .param("routeKey", routeKey)
                .param("httpMethod", httpMethod)
                .param("statusBucket", statusBucket)
                .param("seenAt", Timestamp.from(seenAt))
                .update();
    }

    public long countTotalForDate(long clientId, LocalDate usageDate) {
        Long count = jdbcClient.sql("""
                        SELECT COALESCE(SUM(request_count), 0)
                        FROM fairvalue.api_usage_daily
                        WHERE client_id = :clientId
                          AND usage_date = :usageDate
                        """)
                .param("clientId", clientId)
                .param("usageDate", Date.valueOf(usageDate))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public Instant latestSeenAt(long clientId, LocalDate usageDate) {
        return jdbcClient.sql("""
                        SELECT MAX(last_seen_at)
                        FROM fairvalue.api_usage_daily
                        WHERE client_id = :clientId
                          AND usage_date = :usageDate
                        """)
                .param("clientId", clientId)
                .param("usageDate", Date.valueOf(usageDate))
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    public List<ApiUsageRouteRecord> findRoutesForDate(long clientId, LocalDate usageDate, int limit) {
        return jdbcClient.sql("""
                        SELECT usage_date,
                               route_key,
                               http_method,
                               status_bucket,
                               request_count,
                               last_seen_at
                        FROM fairvalue.api_usage_daily
                        WHERE client_id = :clientId
                          AND usage_date = :usageDate
                        ORDER BY request_count DESC, route_key ASC
                        LIMIT :limit
                        """)
                .param("clientId", clientId)
                .param("usageDate", Date.valueOf(usageDate))
                .param("limit", limit)
                .query((rs, rowNum) -> new ApiUsageRouteRecord(
                        rs.getDate("usage_date").toLocalDate(),
                        rs.getString("route_key"),
                        rs.getString("http_method"),
                        rs.getString("status_bucket"),
                        rs.getLong("request_count"),
                        rs.getTimestamp("last_seen_at").toInstant()
                ))
                .list();
    }
}
