package com.fairvalue.engine.repository;

import com.fairvalue.engine.us.UsValuationJobRecord;
import com.fairvalue.engine.us.UsValuationJobSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ValuationJobRepository {
    private final JdbcClient jdbcClient;

    public ValuationJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertQueued(String jobType, long securityId, String payloadJson) {
        return insertQueued(jobType, securityId, payloadJson, 100, Instant.now());
    }

    public long insertQueued(String jobType, long securityId, String payloadJson, int priority, Instant availableAt) {
        Long id = jdbcClient.sql("""
                        INSERT INTO fairvalue.valuation_jobs (
                            job_type,
                            security_id,
                            status,
                            payload_json,
                            priority,
                            available_at
                        ) VALUES (
                            :jobType,
                            :securityId,
                            'queued',
                            CAST(:payloadJson AS jsonb),
                            :priority,
                            :availableAt
                        )
                        RETURNING id
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .param("payloadJson", emptyJson(payloadJson))
                .param("priority", priority)
                .param("availableAt", Timestamp.from(availableAt == null ? Instant.now() : availableAt))
                .query(Long.class)
                .single();
        return id == null ? 0L : id;
    }

    public void markRunning(long jobId) {
        markRunning(jobId, null);
    }

    public void markRunning(long jobId, String workerId) {
        jdbcClient.sql("""
                        UPDATE fairvalue.valuation_jobs
                        SET status = 'running',
                            started_at = NOW(),
                            finished_at = NULL,
                            error_message = NULL,
                            worker_id = COALESCE(:workerId, worker_id)
                        WHERE id = :jobId
                        """)
                .param("workerId", workerId)
                .param("jobId", jobId)
                .update();
    }

    public void markCompleted(long jobId, Long valuationRunId, String payloadJson) {
        jdbcClient.sql("""
                        UPDATE fairvalue.valuation_jobs
                        SET status = 'completed',
                            valuation_run_id = :valuationRunId,
                            finished_at = NOW(),
                            payload_json = CAST(:payloadJson AS jsonb),
                            worker_id = NULL
                        WHERE id = :jobId
                        """)
                .param("jobId", jobId)
                .param("valuationRunId", valuationRunId)
                .param("payloadJson", emptyJson(payloadJson))
                .update();
    }

    public void markFailed(long jobId, int retryCount, String errorMessage, String payloadJson) {
        markFailed(jobId, retryCount, errorMessage, payloadJson, Instant.now());
    }

    public void markFailed(long jobId, int retryCount, String errorMessage, String payloadJson, Instant availableAt) {
        jdbcClient.sql("""
                        UPDATE fairvalue.valuation_jobs
                        SET status = 'failed',
                            retry_count = :retryCount,
                            error_message = :errorMessage,
                            available_at = :availableAt,
                            finished_at = NOW(),
                            payload_json = CAST(:payloadJson AS jsonb),
                            worker_id = NULL
                        WHERE id = :jobId
                        """)
                .param("jobId", jobId)
                .param("retryCount", retryCount)
                .param("errorMessage", truncate(errorMessage))
                .param("availableAt", Timestamp.from(availableAt == null ? Instant.now() : availableAt))
                .param("payloadJson", emptyJson(payloadJson))
                .update();
    }

    public void markDeadLetter(long jobId, int retryCount, String errorMessage, String payloadJson) {
        jdbcClient.sql("""
                        UPDATE fairvalue.valuation_jobs
                        SET status = 'dead_letter',
                            retry_count = :retryCount,
                            error_message = :errorMessage,
                            finished_at = NOW(),
                            payload_json = CAST(:payloadJson AS jsonb),
                            worker_id = NULL
                        WHERE id = :jobId
                        """)
                .param("jobId", jobId)
                .param("retryCount", retryCount)
                .param("errorMessage", truncate(errorMessage))
                .param("payloadJson", emptyJson(payloadJson))
                .update();
    }

    public long countBySecurityId(long securityId) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE security_id = :securityId
                        """)
                .param("securityId", securityId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countByStatus(String jobType, String status) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE job_type = :jobType
                          AND status = :status
                        """)
                .param("jobType", jobType)
                .param("status", status)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countQueuedReady(String jobType, Instant readyBefore) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE job_type = :jobType
                          AND status = 'queued'
                          AND available_at <= :readyBefore
                        """)
                .param("jobType", jobType)
                .param("readyBefore", Timestamp.from(readyBefore))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countRunningStartedBefore(String jobType, Instant startedBefore) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE job_type = :jobType
                          AND status = 'running'
                          AND started_at IS NOT NULL
                          AND started_at < :startedBefore
                        """)
                .param("jobType", jobType)
                .param("startedBefore", Timestamp.from(startedBefore))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countFailedCreatedAfter(String jobType, Instant createdAfter) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE job_type = :jobType
                          AND status = 'failed'
                          AND created_at >= :createdAfter
                        """)
                .param("jobType", jobType)
                .param("createdAfter", Timestamp.from(createdAfter))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public long countDeadLetterCreatedAfter(String jobType, Instant createdAfter) {
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fairvalue.valuation_jobs
                        WHERE job_type = :jobType
                          AND status = 'dead_letter'
                          AND created_at >= :createdAfter
                        """)
                .param("jobType", jobType)
                .param("createdAfter", Timestamp.from(createdAfter))
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public boolean hasOpenJob(String jobType, long securityId) {
        Boolean exists = jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND security_id = :securityId
                              AND status IN ('queued', 'running')
                        )
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .query(Boolean.class)
                .single();
        return exists != null && exists;
    }

    public List<UsValuationJobRecord> claimQueuedJobs(String jobType, Instant readyBefore, int limit, String workerId) {
        return jdbcClient.sql("""
                        WITH selected AS (
                            SELECT id
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND status = 'queued'
                              AND available_at <= :readyBefore
                            ORDER BY priority DESC, available_at ASC, created_at ASC
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE fairvalue.valuation_jobs vj
                        SET status = 'running',
                            started_at = NOW(),
                            finished_at = NULL,
                            error_message = NULL,
                            worker_id = :workerId
                        FROM selected
                        WHERE vj.id = selected.id
                        RETURNING vj.id,
                                  vj.job_type,
                                  vj.security_id,
                                  (SELECT ticker FROM fairvalue.security_master sm WHERE sm.id = vj.security_id) AS ticker,
                                  vj.status,
                                  vj.valuation_run_id,
                                  vj.priority,
                                  vj.available_at,
                                  vj.worker_id,
                                  vj.started_at,
                                  vj.finished_at,
                                  vj.error_message,
                                  vj.retry_count,
                                  vj.payload_json::text AS payload_json,
                                  vj.created_at
                        """)
                .param("jobType", jobType)
                .param("readyBefore", Timestamp.from(readyBefore))
                .param("limit", limit)
                .param("workerId", workerId)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> claimQueuedJobsBySecurityId(
            String jobType,
            long securityId,
            Instant readyBefore,
            int limit,
            String workerId
    ) {
        return jdbcClient.sql("""
                        WITH selected AS (
                            SELECT id
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND security_id = :securityId
                              AND status = 'queued'
                              AND available_at <= :readyBefore
                            ORDER BY priority DESC, available_at ASC, created_at ASC
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE fairvalue.valuation_jobs vj
                        SET status = 'running',
                            started_at = NOW(),
                            finished_at = NULL,
                            error_message = NULL,
                            worker_id = :workerId
                        FROM selected
                        WHERE vj.id = selected.id
                        RETURNING vj.id,
                                  vj.job_type,
                                  vj.security_id,
                                  (SELECT ticker FROM fairvalue.security_master sm WHERE sm.id = vj.security_id) AS ticker,
                                  vj.status,
                                  vj.valuation_run_id,
                                  vj.priority,
                                  vj.available_at,
                                  vj.worker_id,
                                  vj.started_at,
                                  vj.finished_at,
                                  vj.error_message,
                                  vj.retry_count,
                                  vj.payload_json::text AS payload_json,
                                  vj.created_at
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .param("readyBefore", Timestamp.from(readyBefore))
                .param("limit", limit)
                .param("workerId", workerId)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> requeueRetryableFailedJobs(
            String jobType,
            int maxRetryCount,
            Instant createdAfter,
            int limit,
            Instant availableAt,
            int priority
    ) {
        return jdbcClient.sql("""
                        WITH selected AS (
                            SELECT id
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND status = 'failed'
                              AND retry_count < :maxRetryCount
                              AND created_at >= :createdAfter
                              AND available_at <= :readyBefore
                            ORDER BY finished_at ASC NULLS FIRST, id ASC
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE fairvalue.valuation_jobs vj
                        SET status = 'queued',
                            available_at = :availableAt,
                            priority = GREATEST(vj.priority, :priority),
                            worker_id = NULL,
                            started_at = NULL,
                            finished_at = NULL
                        FROM selected
                        WHERE vj.id = selected.id
                        RETURNING vj.id,
                                  vj.job_type,
                                  vj.security_id,
                                  (SELECT ticker FROM fairvalue.security_master sm WHERE sm.id = vj.security_id) AS ticker,
                                  vj.status,
                                  vj.valuation_run_id,
                                  vj.priority,
                                  vj.available_at,
                                  vj.worker_id,
                                  vj.started_at,
                                  vj.finished_at,
                                  vj.error_message,
                                  vj.retry_count,
                                  vj.payload_json::text AS payload_json,
                                  vj.created_at
                        """)
                .param("jobType", jobType)
                .param("maxRetryCount", maxRetryCount)
                .param("createdAfter", Timestamp.from(createdAfter))
                .param("readyBefore", Timestamp.from(availableAt == null ? Instant.now() : availableAt))
                .param("limit", limit)
                .param("availableAt", Timestamp.from(availableAt))
                .param("priority", priority)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> requeueRetryableFailedJobsBySecurityId(
            String jobType,
            long securityId,
            int maxRetryCount,
            Instant createdAfter,
            int limit,
            Instant availableAt,
            int priority
    ) {
        return jdbcClient.sql("""
                        WITH selected AS (
                            SELECT id
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND security_id = :securityId
                              AND status = 'failed'
                              AND retry_count < :maxRetryCount
                              AND created_at >= :createdAfter
                              AND available_at <= :readyBefore
                            ORDER BY finished_at ASC NULLS FIRST, id ASC
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE fairvalue.valuation_jobs vj
                        SET status = 'queued',
                            available_at = :availableAt,
                            priority = GREATEST(vj.priority, :priority),
                            worker_id = NULL,
                            started_at = NULL,
                            finished_at = NULL
                        FROM selected
                        WHERE vj.id = selected.id
                        RETURNING vj.id,
                                  vj.job_type,
                                  vj.security_id,
                                  (SELECT ticker FROM fairvalue.security_master sm WHERE sm.id = vj.security_id) AS ticker,
                                  vj.status,
                                  vj.valuation_run_id,
                                  vj.priority,
                                  vj.available_at,
                                  vj.worker_id,
                                  vj.started_at,
                                  vj.finished_at,
                                  vj.error_message,
                                  vj.retry_count,
                                  vj.payload_json::text AS payload_json,
                                  vj.created_at
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .param("maxRetryCount", maxRetryCount)
                .param("createdAfter", Timestamp.from(createdAfter))
                .param("readyBefore", Timestamp.from(availableAt == null ? Instant.now() : availableAt))
                .param("limit", limit)
                .param("availableAt", Timestamp.from(availableAt))
                .param("priority", priority)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> requeueStaleRunningJobs(
            String jobType,
            Instant startedBefore,
            int limit,
            Instant availableAt,
            int priority
    ) {
        return jdbcClient.sql("""
                        WITH selected AS (
                            SELECT id
                            FROM fairvalue.valuation_jobs
                            WHERE job_type = :jobType
                              AND status = 'running'
                              AND started_at IS NOT NULL
                              AND started_at < :startedBefore
                            ORDER BY started_at ASC, id ASC
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE fairvalue.valuation_jobs vj
                        SET status = 'queued',
                            retry_count = vj.retry_count + 1,
                            error_message = 'stale_running_timeout',
                            available_at = :availableAt,
                            priority = GREATEST(vj.priority, :priority),
                            worker_id = NULL,
                            started_at = NULL,
                            finished_at = NULL
                        FROM selected
                        WHERE vj.id = selected.id
                        RETURNING vj.id,
                                  vj.job_type,
                                  vj.security_id,
                                  (SELECT ticker FROM fairvalue.security_master sm WHERE sm.id = vj.security_id) AS ticker,
                                  vj.status,
                                  vj.valuation_run_id,
                                  vj.priority,
                                  vj.available_at,
                                  vj.worker_id,
                                  vj.started_at,
                                  vj.finished_at,
                                  vj.error_message,
                                  vj.retry_count,
                                  vj.payload_json::text AS payload_json,
                                  vj.created_at
                        """)
                .param("jobType", jobType)
                .param("startedBefore", Timestamp.from(startedBefore))
                .param("limit", limit)
                .param("availableAt", Timestamp.from(availableAt))
                .param("priority", priority)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findRecent(String jobType, int limit) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE (:jobType IS NULL OR vj.job_type = :jobType)
                        ORDER BY vj.created_at DESC, vj.id DESC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findRecentBySecurityId(String jobType, long securityId, int limit) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE vj.security_id = :securityId
                          AND (:jobType IS NULL OR vj.job_type = :jobType)
                        ORDER BY vj.created_at DESC, vj.id DESC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findRetryableFailedJobs(String jobType, int maxRetryCount, Instant createdAfter, int limit) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE vj.job_type = :jobType
                          AND vj.status = 'failed'
                          AND vj.retry_count < :maxRetryCount
                          AND vj.created_at >= :createdAfter
                          AND vj.available_at <= NOW()
                        ORDER BY vj.finished_at ASC NULLS FIRST, vj.id ASC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("maxRetryCount", maxRetryCount)
                .param("createdAfter", Timestamp.from(createdAfter))
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findRetryableFailedJobsBySecurityId(
            String jobType,
            long securityId,
            int maxRetryCount,
            Instant createdAfter,
            int limit
    ) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE vj.job_type = :jobType
                          AND vj.security_id = :securityId
                          AND vj.status = 'failed'
                          AND vj.retry_count < :maxRetryCount
                          AND vj.created_at >= :createdAfter
                          AND vj.available_at <= NOW()
                        ORDER BY vj.finished_at ASC NULLS FIRST, vj.id ASC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("securityId", securityId)
                .param("maxRetryCount", maxRetryCount)
                .param("createdAfter", Timestamp.from(createdAfter))
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findStaleRunningJobs(String jobType, Instant startedBefore, int limit) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE vj.job_type = :jobType
                          AND vj.status = 'running'
                          AND vj.started_at IS NOT NULL
                          AND vj.started_at < :startedBefore
                        ORDER BY vj.started_at ASC, vj.id ASC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("startedBefore", Timestamp.from(startedBefore))
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public List<UsValuationJobRecord> findDeadLetterJobs(String jobType, int limit) {
        return jdbcClient.sql("""
                        SELECT vj.id,
                               vj.job_type,
                               vj.security_id,
                               sm.ticker,
                               vj.status,
                               vj.valuation_run_id,
                               vj.priority,
                               vj.available_at,
                               vj.worker_id,
                               vj.started_at,
                               vj.finished_at,
                               vj.error_message,
                               vj.retry_count,
                               vj.payload_json::text AS payload_json,
                               vj.created_at
                        FROM fairvalue.valuation_jobs vj
                        LEFT JOIN fairvalue.security_master sm
                          ON sm.id = vj.security_id
                        WHERE (:jobType IS NULL OR vj.job_type = :jobType)
                          AND vj.status = 'dead_letter'
                        ORDER BY vj.finished_at DESC NULLS LAST, vj.id DESC
                        LIMIT :limit
                        """)
                .param("jobType", jobType)
                .param("limit", limit)
                .query(this::mapJob)
                .list();
    }

    public UsValuationJobSummary summarize(
            String jobType,
            Instant createdAfter,
            int maxRetryCount,
            Instant readyBefore,
            Instant startedBefore
    ) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FILTER (WHERE status = 'queued') AS queued_count,
                               COUNT(*) FILTER (WHERE status = 'running') AS running_count,
                               COUNT(*) FILTER (WHERE status = 'completed') AS completed_count,
                               COUNT(*) FILTER (WHERE status = 'failed') AS failed_count,
                               COUNT(*) FILTER (WHERE status = 'failed' AND retry_count < :maxRetryCount) AS retryable_count,
                               COUNT(*) FILTER (WHERE status = 'dead_letter') AS dead_letter_count,
                               COUNT(*) FILTER (WHERE status = 'queued' AND available_at <= :readyBefore) AS ready_backlog_count,
                               COUNT(*) FILTER (WHERE status = 'running' AND started_at IS NOT NULL AND started_at < :startedBefore) AS stale_running_count,
                               COUNT(*) FILTER (WHERE status = 'failed' AND retry_count >= :maxRetryCount) AS repeated_failed_count,
                               COUNT(*) AS total_count
                        FROM fairvalue.valuation_jobs
                        WHERE (:jobType IS NULL OR job_type = :jobType)
                          AND created_at >= :createdAfter
                        """)
                .param("jobType", jobType)
                .param("createdAfter", Timestamp.from(createdAfter))
                .param("maxRetryCount", maxRetryCount)
                .param("readyBefore", Timestamp.from(readyBefore))
                .param("startedBefore", Timestamp.from(startedBefore))
                .query((rs, rowNum) -> new UsValuationJobSummary(
                        rs.getLong("queued_count"),
                        rs.getLong("running_count"),
                        rs.getLong("completed_count"),
                        rs.getLong("failed_count"),
                        rs.getLong("retryable_count"),
                        rs.getLong("dead_letter_count"),
                        rs.getLong("ready_backlog_count"),
                        rs.getLong("stale_running_count"),
                        rs.getLong("repeated_failed_count"),
                        rs.getLong("total_count")
                ))
                .single();
    }

    private UsValuationJobRecord mapJob(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UsValuationJobRecord(
                rs.getLong("id"),
                rs.getString("job_type"),
                rs.getObject("security_id") == null ? null : rs.getLong("security_id"),
                rs.getString("ticker"),
                rs.getString("status"),
                rs.getObject("valuation_run_id") == null ? null : rs.getLong("valuation_run_id"),
                rs.getInt("priority"),
                rs.getTimestamp("available_at") == null ? null : rs.getTimestamp("available_at").toInstant(),
                rs.getString("worker_id"),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                rs.getString("error_message"),
                rs.getInt("retry_count"),
                rs.getString("payload_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String emptyJson(String payloadJson) {
        return payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
