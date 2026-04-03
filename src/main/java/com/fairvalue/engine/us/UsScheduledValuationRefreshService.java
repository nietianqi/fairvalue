package com.fairvalue.engine.us;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "app.valuation.us.schedule.enabled", havingValue = "true")
public class UsScheduledValuationRefreshService {
    private static final Logger log = LoggerFactory.getLogger(UsScheduledValuationRefreshService.class);

    private final MarketDataService marketDataService;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsValuationJobService usValuationJobService;
    private final UsValuationAlertService usValuationAlertService;
    private final int maxSymbols;
    private final int maxRetries;
    private final int retryLookbackHours;
    private final int staleRunningMinutes;
    private final int workerBatchSize;
    private final int freshJobPriority = 100;
    private final AtomicBoolean enqueueRunning = new AtomicBoolean(false);
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final AtomicBoolean recoveryRunning = new AtomicBoolean(false);
    private final AtomicBoolean alertRunning = new AtomicBoolean(false);

    public UsScheduledValuationRefreshService(
            MarketDataService marketDataService,
            UsSecurityMasterService usSecurityMasterService,
            UsValuationJobService usValuationJobService,
            UsValuationAlertService usValuationAlertService,
            @Value("${app.valuation.us.schedule.max-symbols:200}") int maxSymbols,
            @Value("${app.valuation.us.schedule.max-retries:2}") int maxRetries,
            @Value("${app.valuation.us.schedule.retry-lookback-hours:24}") int retryLookbackHours,
            @Value("${app.valuation.us.schedule.stale-running-minutes:90}") int staleRunningMinutes,
            @Value("${app.valuation.us.worker.batch-size:12}") int workerBatchSize
    ) {
        this.marketDataService = marketDataService;
        this.usSecurityMasterService = usSecurityMasterService;
        this.usValuationJobService = usValuationJobService;
        this.usValuationAlertService = usValuationAlertService;
        this.maxSymbols = maxSymbols;
        this.maxRetries = maxRetries;
        this.retryLookbackHours = retryLookbackHours;
        this.staleRunningMinutes = staleRunningMinutes;
        this.workerBatchSize = workerBatchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.valuation.us.schedule.fixed-delay-ms:1800000}",
            initialDelayString = "${app.valuation.us.schedule.initial-delay-ms:120000}"
    )
    public void refreshActiveUniverse() {
        if (!enqueueRunning.compareAndSet(false, true)) {
            log.info("US scheduled valuation enqueue skipped because a previous cycle is still running.");
            return;
        }
        try {
            List<UsValuationJobRecord> requeuedRetryableJobs = usValuationJobService.requeueRetryableFailedJobs(
                    maxRetries,
                    retryLookbackHours,
                    queueScopeLimit()
            );
            Set<String> tickers = new LinkedHashSet<>();
            usSecurityMasterService.findScheduledUniverseTickers(maxSymbols).stream()
                    .filter(symbol -> symbol != null && !symbol.isBlank())
                    .forEach(tickers::add);
            tickers.removeIf(symbol -> {
                try {
                    return usValuationJobService.hasOpenJob(symbol);
                } catch (Exception ignored) {
                    return false;
                }
            });

            log.info(
                    "US scheduled valuation enqueue started for {} fresh tickers, {} requeued retryable jobs, recovered stale jobs={}.",
                    tickers.size(),
                    requeuedRetryableJobs.size(),
                    0
            );

            for (String ticker : tickers) {
                try {
                    usValuationJobService.enqueueScheduledRefresh(ticker, "scheduled_cycle", freshJobPriority, java.time.Instant.now());
                } catch (Exception ex) {
                    log.warn("US scheduled valuation enqueue failed for {}: {}", ticker, ex.getMessage());
                }
            }
            log.info("US scheduled valuation enqueue completed.");
        } finally {
            enqueueRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.valuation.us.worker.fixed-delay-ms:15000}",
            initialDelayString = "${app.valuation.us.worker.initial-delay-ms:30000}"
    )
    public void processQueuedJobs() {
        if (!workerRunning.compareAndSet(false, true)) {
            log.info("US valuation worker skipped because a previous worker cycle is still running.");
            return;
        }
        try {
            runWorkerCycle(workerBatchSize);
        } finally {
            workerRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.valuation.us.worker.recovery-fixed-delay-ms:60000}",
            initialDelayString = "${app.valuation.us.worker.recovery-initial-delay-ms:45000}"
    )
    public void recoverStaleJobs() {
        if (!recoveryRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            List<UsValuationJobRecord> recovered = usValuationJobService.recoverStaleRunningJobs(
                    staleRunningMinutes,
                    queueScopeLimit()
            );
            if (!recovered.isEmpty()) {
                log.warn("US valuation stale recovery requeued {} jobs.", recovered.size());
            }
        } finally {
            recoveryRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.valuation.us.worker.alert-fixed-delay-ms:60000}",
            initialDelayString = "${app.valuation.us.worker.alert-initial-delay-ms:60000}"
    )
    public void evaluateAlerts() {
        if (!alertRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            UsValuationAlertService.AlertSummary summary = usValuationAlertService.summarize();
            if (!summary.alerts().isEmpty()) {
                log.warn(
                        "US valuation alerts present. queued={}, running={}, dead_letter={}, alerts={}",
                        summary.queuedCount(),
                        summary.runningCount(),
                        summary.deadLetterCount(),
                        summary.alerts().size()
                );
            }
        } finally {
            alertRunning.set(false);
        }
    }

    private void runWorkerCycle(int claimLimit) {
        List<UsValuationJobService.JobExecutionResult> processed = new ArrayList<>();
        List<UsValuationJobRecord> requeuedRetryableJobs = usValuationJobService.requeueRetryableFailedJobs(
                maxRetries,
                retryLookbackHours,
                Math.max(claimLimit, queueScopeLimit())
        );

        List<UsValuationJobRecord> claimedJobs = usValuationJobService.claimQueuedJobs(
                "worker-" + UUID.randomUUID(),
                claimLimit
        );
        for (UsValuationJobRecord claimedJob : claimedJobs) {
            try {
                String trigger = claimedJob.retryCount() > 0 ? "retry" : "scheduled_cycle";
                processed.add(usValuationJobService.executeClaimedJob(claimedJob, trigger));
            } catch (Exception ex) {
                log.warn("US valuation worker execution failed for job {} / {}: {}", claimedJob.id(), claimedJob.ticker(), ex.getMessage());
            }
        }
        long completed = processed.stream().filter(result -> "completed".equalsIgnoreCase(result.status())).count();
        long failed = processed.stream().filter(result -> "failed".equalsIgnoreCase(result.status())).count();
        log.info(
                "US valuation worker cycle completed. claimed={}, completed={}, failed={}, requeued_retryable={}, recovered_stale={}",
                claimedJobs.size(),
                completed,
                failed,
                requeuedRetryableJobs.size(),
                0
        );
    }

    private int queueScopeLimit() {
        return maxSymbols <= 0 ? 20000 : maxSymbols;
    }
}
