package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.ValuationJobRepository;
import com.fairvalue.engine.service.MarketDiscoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsValuationAlertService {
    private final ValuationJobRepository valuationJobRepository;
    private final int backlogThreshold;
    private final int failedThreshold;
    private final int staleMinutes;
    private final int failedLookbackHours;
    private final double rankingCoverageThreshold;
    private final MarketDiscoveryService marketDiscoveryService;

    public UsValuationAlertService(
            ValuationJobRepository valuationJobRepository,
            MarketDiscoveryService marketDiscoveryService,
            @Value("${app.valuation.us.worker.backlog-alert-threshold:20}") int backlogThreshold,
            @Value("${app.valuation.us.worker.failed-alert-threshold:5}") int failedThreshold,
            @Value("${app.valuation.us.schedule.stale-running-minutes:90}") int staleMinutes,
            @Value("${app.valuation.us.schedule.retry-lookback-hours:24}") int failedLookbackHours,
            @Value("${app.valuation.us.rankings.min-coverage-ratio:0.60}") double rankingCoverageThreshold
    ) {
        this.valuationJobRepository = valuationJobRepository;
        this.marketDiscoveryService = marketDiscoveryService;
        this.backlogThreshold = backlogThreshold;
        this.failedThreshold = failedThreshold;
        this.staleMinutes = staleMinutes;
        this.failedLookbackHours = failedLookbackHours;
        this.rankingCoverageThreshold = rankingCoverageThreshold;
    }

    public AlertSummary summarize() {
        Instant now = Instant.now();
        long queued = valuationJobRepository.countByStatus(UsValuationJobService.JOB_TYPE, "queued");
        long running = valuationJobRepository.countByStatus(UsValuationJobService.JOB_TYPE, "running");
        long deadLetter = valuationJobRepository.countByStatus(UsValuationJobService.JOB_TYPE, "dead_letter");
        long readyBacklog = valuationJobRepository.countQueuedReady(UsValuationJobService.JOB_TYPE, now);
        long staleRunning = valuationJobRepository.countRunningStartedBefore(
                UsValuationJobService.JOB_TYPE,
                now.minus(staleMinutes, ChronoUnit.MINUTES)
        );
        long recentFailures = valuationJobRepository.countFailedCreatedAfter(
                UsValuationJobService.JOB_TYPE,
                now.minus(failedLookbackHours, ChronoUnit.HOURS)
        );
        UsRankingCoverageMetrics coverage = marketDiscoveryService.usRankingCoverage();

        List<AlertItem> alerts = new ArrayList<>();
        if (staleRunning > 0) {
            alerts.add(new AlertItem(
                    "critical",
                    "stale_running_jobs",
                    "Stale running jobs",
                    "There are valuation jobs stuck in running state beyond the stale threshold.",
                    staleRunning,
                    now,
                    Map.of("stale_minutes", staleMinutes)
            ));
        }
        if (readyBacklog >= backlogThreshold) {
            alerts.add(new AlertItem(
                    "warning",
                    "queued_backlog",
                    "Queued backlog",
                    "Queued valuation backlog is above the configured alert threshold.",
                    readyBacklog,
                    now,
                    Map.of("backlog_threshold", backlogThreshold)
            ));
        }
        if (recentFailures >= failedThreshold) {
            alerts.add(new AlertItem(
                    "warning",
                    "failure_burst",
                    "Recent failure burst",
                    "Recent valuation failures exceeded the configured threshold.",
                    recentFailures,
                    now,
                    Map.of("window_hours", failedLookbackHours, "failure_threshold", failedThreshold)
            ));
        }
        if (deadLetter > 0) {
            alerts.add(new AlertItem(
                    "critical",
                    "dead_letter_jobs",
                    "Dead-letter jobs detected",
                    "One or more valuation jobs exhausted retry budget and require manual review.",
                    deadLetter,
                    now,
                    Map.of("retry_policy", "terminal_after_retry_ceiling")
            ));
        }
        if (coverage.rankableCoverage() < rankingCoverageThreshold) {
            alerts.add(new AlertItem(
                    "warning",
                    "low_snapshot_coverage",
                    "Ranking coverage below strict threshold",
                    "US strict ranking coverage is below the configured threshold, so rankings remain transitional.",
                    coverage.rankableCount(),
                    now,
                    Map.of(
                            "rankable_coverage", coverage.rankableCoverage(),
                            "snapshot_coverage", coverage.snapshotCoverage(),
                            "required_coverage", rankingCoverageThreshold
                    )
            ));
        }

        return new AlertSummary(
                now,
                queued,
                running,
                readyBacklog,
                staleRunning,
                recentFailures,
                deadLetter,
                alerts
        );
    }

    public record AlertSummary(
            Instant asOf,
            long queuedCount,
            long runningCount,
            long readyBacklogCount,
            long staleRunningCount,
            long recentFailedCount,
            long deadLetterCount,
            List<AlertItem> alerts
    ) {
    }

    public record AlertItem(
            String severity,
            String code,
            String title,
            String detail,
            long affectedCount,
            Instant asOf,
            Map<String, Object> details
    ) {
    }
}
