package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.ValuationJobRepository;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UsValuationJobService {
    public static final String JOB_TYPE = "us_scheduled_valuation_refresh";
    private static final int DEFAULT_PRIORITY = 100;
    private static final int RETRY_PRIORITY = 220;
    private static final int STALE_RECOVERY_PRIORITY = 260;
    private static final int MANUAL_PRIORITY = 320;

    private final ValuationJobRepository valuationJobRepository;
    private final ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsEquityValuationService usEquityValuationService;
    private final ObjectMapper objectMapper;
    private final int retryCeiling;
    private final int retryBackoffBaseSeconds;
    private final int retryBackoffMaxSeconds;

    public UsValuationJobService(
            ValuationJobRepository valuationJobRepository,
            ValuationLatestSnapshotRepository valuationLatestSnapshotRepository,
            UsSecurityMasterService usSecurityMasterService,
            UsEquityValuationService usEquityValuationService,
            ObjectMapper objectMapper,
            @Value("${app.valuation.us.schedule.max-retries:3}") int retryCeiling,
            @Value("${app.valuation.us.worker.retry-backoff-base-seconds:60}") int retryBackoffBaseSeconds,
            @Value("${app.valuation.us.worker.retry-backoff-max-seconds:1800}") int retryBackoffMaxSeconds
    ) {
        this.valuationJobRepository = valuationJobRepository;
        this.valuationLatestSnapshotRepository = valuationLatestSnapshotRepository;
        this.usSecurityMasterService = usSecurityMasterService;
        this.usEquityValuationService = usEquityValuationService;
        this.objectMapper = objectMapper;
        this.retryCeiling = Math.max(1, retryCeiling);
        this.retryBackoffBaseSeconds = Math.max(5, retryBackoffBaseSeconds);
        this.retryBackoffMaxSeconds = Math.max(this.retryBackoffBaseSeconds, retryBackoffMaxSeconds);
    }

    public long enqueueScheduledRefresh(String ticker, String trigger) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        return enqueueScheduledRefresh(ticker, trigger, DEFAULT_PRIORITY, Instant.now());
    }

    public long enqueueScheduledRefresh(String ticker, String trigger, int priority, Instant availableAt) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        return valuationJobRepository.insertQueued(JOB_TYPE, securityId, toJson(basePayload(ticker, trigger)), priority, availableAt);
    }

    public boolean hasOpenJob(String ticker) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        return valuationJobRepository.hasOpenJob(JOB_TYPE, securityId);
    }

    public JobExecutionResult executeScheduledRefresh(long jobId, String ticker, String trigger, int currentRetryCount) {
        valuationJobRepository.markRunning(jobId);
        return executeRunningJob(jobId, ticker, trigger, currentRetryCount);
    }

    public JobExecutionResult executeClaimedJob(UsValuationJobRecord job, String trigger) {
        return executeRunningJob(job.id(), job.ticker(), trigger, job.retryCount());
    }

    private JobExecutionResult executeRunningJob(long jobId, String ticker, String trigger, int currentRetryCount) {
        try {
            usEquityValuationService.runScheduledValuation(ticker);
            Long latestRunId = valuationLatestSnapshotRepository.findByTicker(ticker)
                    .map(UsStoredValuationSnapshotRecord::latestRunId)
                    .orElse(null);
            Map<String, Object> payload = basePayload(ticker, trigger);
            payload.put("completed_at", Instant.now().toString());
            payload.put("latest_run_id", latestRunId);
            payload.put("status", "completed");
            valuationJobRepository.markCompleted(jobId, latestRunId, toJson(payload));
            return new JobExecutionResult(jobId, ticker, "completed", latestRunId, currentRetryCount, null);
        } catch (Exception ex) {
            int nextRetryCount = currentRetryCount + 1;
            Map<String, Object> payload = basePayload(ticker, trigger);
            payload.put("failed_at", Instant.now().toString());
            payload.put("status", nextRetryCount >= retryCeiling ? "dead_letter" : "failed");
            payload.put("error_message", ex.getMessage());
            if (nextRetryCount >= retryCeiling) {
                valuationJobRepository.markDeadLetter(jobId, nextRetryCount, ex.getMessage(), toJson(payload));
                return new JobExecutionResult(jobId, ticker, "dead_letter", null, nextRetryCount, ex.getMessage());
            }
            valuationJobRepository.markFailed(jobId, nextRetryCount, ex.getMessage(), toJson(payload), nextRetryAt(nextRetryCount));
            return new JobExecutionResult(jobId, ticker, "failed", null, nextRetryCount, ex.getMessage());
        }
    }

    public List<UsValuationJobRecord> recentJobs(int limit) {
        return valuationJobRepository.findRecent(JOB_TYPE, limit);
    }

    public List<UsValuationJobRecord> recentJobsForTicker(String ticker, int limit) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        return valuationJobRepository.findRecentBySecurityId(JOB_TYPE, securityId, limit);
    }

    public List<UsValuationJobRecord> retryableJobs(int maxRetryCount, int lookbackHours, int limit) {
        Instant createdAfter = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        return valuationJobRepository.findRetryableFailedJobs(JOB_TYPE, Math.min(Math.max(1, maxRetryCount), retryCeiling), createdAfter, limit);
    }

    public List<UsValuationJobRecord> requeueRetryableFailedJobs(int maxRetryCount, int lookbackHours, int limit) {
        Instant createdAfter = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        return valuationJobRepository.requeueRetryableFailedJobs(
                JOB_TYPE,
                Math.min(Math.max(1, maxRetryCount), retryCeiling),
                createdAfter,
                limit,
                Instant.now(),
                RETRY_PRIORITY
        );
    }

    public List<JobExecutionResult> retryFailedJobs(int maxRetryCount, int lookbackHours, int limit) {
        Instant createdAfter = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        valuationJobRepository.requeueRetryableFailedJobs(
                JOB_TYPE,
                Math.min(Math.max(1, maxRetryCount), retryCeiling),
                createdAfter,
                limit,
                Instant.now(),
                MANUAL_PRIORITY
        );
        String workerId = nextWorkerId("manual-retry");
        List<UsValuationJobRecord> claimed = valuationJobRepository.claimQueuedJobs(JOB_TYPE, Instant.now(), limit, workerId);
        return executeClaimedJobs(claimed, "manual_retry");
    }

    public List<JobExecutionResult> retryFailedJobsForTicker(String ticker, int maxRetryCount, int lookbackHours, int limit) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        Instant createdAfter = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        valuationJobRepository.requeueRetryableFailedJobsBySecurityId(
                JOB_TYPE,
                securityId,
                Math.min(Math.max(1, maxRetryCount), retryCeiling),
                createdAfter,
                limit,
                Instant.now(),
                MANUAL_PRIORITY
        );
        String workerId = nextWorkerId("manual-retry-ticker");
        List<UsValuationJobRecord> claimed = valuationJobRepository.claimQueuedJobsBySecurityId(
                JOB_TYPE,
                securityId,
                Instant.now(),
                limit,
                workerId
        );
        return executeClaimedJobs(claimed, "manual_retry");
    }

    public List<UsValuationJobRecord> recoverStaleRunningJobs(int staleMinutes, int limit) {
        Instant startedBefore = Instant.now().minus(staleMinutes, ChronoUnit.MINUTES);
        List<UsValuationJobRecord> staleJobs = valuationJobRepository.findStaleRunningJobs(JOB_TYPE, startedBefore, limit);
        List<UsValuationJobRecord> requeued = new ArrayList<>();
        for (UsValuationJobRecord staleJob : staleJobs) {
            int nextRetryCount = staleJob.retryCount() + 1;
            if (nextRetryCount >= retryCeiling) {
                Map<String, Object> payload = basePayload(staleJob.ticker(), "stale_recovery");
                payload.put("stale_started_at", staleJob.startedAt() == null ? null : staleJob.startedAt().toString());
                payload.put("status", "dead_letter");
                payload.put("error_message", "stale_running_timeout");
                valuationJobRepository.markDeadLetter(staleJob.id(), nextRetryCount, "stale_running_timeout", toJson(payload));
                continue;
            }
            requeued.addAll(valuationJobRepository.requeueStaleRunningJobs(
                    JOB_TYPE,
                    startedBefore,
                    1,
                    nextRetryAt(nextRetryCount),
                    STALE_RECOVERY_PRIORITY
            ).stream().filter(job -> job.id() == staleJob.id()).toList());
        }
        return requeued;
    }

    public List<UsValuationJobRecord> claimQueuedJobs(String workerId, int limit) {
        return valuationJobRepository.claimQueuedJobs(JOB_TYPE, Instant.now(), limit, workerId);
    }

    public UsValuationJobSummary summary(int lookbackHours, int maxRetryCount) {
        Instant createdAfter = Instant.now().minus(lookbackHours, ChronoUnit.HOURS);
        return valuationJobRepository.summarize(
                JOB_TYPE,
                createdAfter,
                Math.min(Math.max(1, maxRetryCount), retryCeiling),
                Instant.now(),
                Instant.now().minus(90, ChronoUnit.MINUTES)
        );
    }

    public List<UsValuationJobRecord> deadLetterJobs(int limit) {
        return valuationJobRepository.findDeadLetterJobs(JOB_TYPE, Math.max(1, Math.min(limit, 100)));
    }

    public long countForTicker(String ticker) {
        long securityId = usSecurityMasterService.resolveSecurityId(ticker)
                .orElseThrow(() -> new IllegalStateException("Ticker " + ticker + " does not exist in security master."));
        return valuationJobRepository.countBySecurityId(securityId);
    }

    private List<JobExecutionResult> executeClaimedJobs(List<UsValuationJobRecord> claimedJobs, String trigger) {
        List<JobExecutionResult> results = new ArrayList<>();
        for (UsValuationJobRecord job : claimedJobs) {
            results.add(executeClaimedJob(job, trigger));
        }
        return results;
    }

    private Map<String, Object> basePayload(String ticker, String trigger) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("market", "US");
        payload.put("ticker", ticker);
        payload.put("trigger", trigger);
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize valuation job payload.", ex);
        }
    }

    private String nextWorkerId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private Instant nextRetryAt(int retryCount) {
        long seconds = (long) retryBackoffBaseSeconds * (1L << Math.max(0, retryCount - 1));
        seconds = Math.min(seconds, retryBackoffMaxSeconds);
        return Instant.now().plusSeconds(seconds);
    }

    public record JobExecutionResult(
            long jobId,
            String ticker,
            String status,
            Long valuationRunId,
            int retryCount,
            String errorMessage
    ) {
    }
}
