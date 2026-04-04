package com.fairvalue.engine.api;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.service.MarketDiscoveryService;
import com.fairvalue.engine.service.MarketDataService;
import com.fairvalue.engine.us.UsEquityAdminOverviewService;
import com.fairvalue.engine.us.UsCompanyIrSyncService;
import com.fairvalue.engine.us.UsExternalSourceStatusService;
import com.fairvalue.engine.us.UsFinancialStandardizationService;
import com.fairvalue.engine.us.UsRankingCoverageMetrics;
import com.fairvalue.engine.us.UsSecDocumentSyncService;
import com.fairvalue.engine.us.UsSnapshotBackfillService;
import com.fairvalue.engine.us.UsValuationAlertService;
import com.fairvalue.engine.us.UsValuationJobRecord;
import com.fairvalue.engine.us.UsValuationJobService;
import com.fairvalue.engine.us.UsValuationJobSummary;
import com.fairvalue.engine.us.UsUniverseSyncService;
import com.fairvalue.engine.us.UsValuationDiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/us-equities-admin")
public class UsEquityAdminController {
    private final UsUniverseSyncService usUniverseSyncService;
    private final UsSecDocumentSyncService usSecDocumentSyncService;
    private final UsCompanyIrSyncService usCompanyIrSyncService;
    private final UsFinancialStandardizationService usFinancialStandardizationService;
    private final UsEquityAdminOverviewService usEquityAdminOverviewService;
    private final UsExternalSourceStatusService usExternalSourceStatusService;
    private final UsValuationJobService usValuationJobService;
    private final UsValuationAlertService usValuationAlertService;
    private final UsSnapshotBackfillService usSnapshotBackfillService;
    private final UsValuationDiagnosticsService usValuationDiagnosticsService;
    private final MarketDataService marketDataService;
    private final MarketDiscoveryService marketDiscoveryService;

    public UsEquityAdminController(
            UsUniverseSyncService usUniverseSyncService,
            UsSecDocumentSyncService usSecDocumentSyncService,
            UsCompanyIrSyncService usCompanyIrSyncService,
            UsFinancialStandardizationService usFinancialStandardizationService,
            UsEquityAdminOverviewService usEquityAdminOverviewService,
            UsExternalSourceStatusService usExternalSourceStatusService,
            UsValuationJobService usValuationJobService,
            UsValuationAlertService usValuationAlertService,
            UsSnapshotBackfillService usSnapshotBackfillService,
            UsValuationDiagnosticsService usValuationDiagnosticsService,
            MarketDataService marketDataService,
            MarketDiscoveryService marketDiscoveryService
    ) {
        this.usUniverseSyncService = usUniverseSyncService;
        this.usSecDocumentSyncService = usSecDocumentSyncService;
        this.usCompanyIrSyncService = usCompanyIrSyncService;
        this.usFinancialStandardizationService = usFinancialStandardizationService;
        this.usEquityAdminOverviewService = usEquityAdminOverviewService;
        this.usExternalSourceStatusService = usExternalSourceStatusService;
        this.usValuationJobService = usValuationJobService;
        this.usValuationAlertService = usValuationAlertService;
        this.usSnapshotBackfillService = usSnapshotBackfillService;
        this.usValuationDiagnosticsService = usValuationDiagnosticsService;
        this.marketDataService = marketDataService;
        this.marketDiscoveryService = marketDiscoveryService;
    }

    @GetMapping("/{ticker}/overview")
    public UsEquityAdminOverviewService.AdminOverview overview(@PathVariable String ticker) {
        return usEquityAdminOverviewService.overview(ticker);
    }

    @GetMapping("/{ticker}/source-status")
    public UsExternalSourceStatusService.ExternalSourceStatus sourceStatus(@PathVariable String ticker) {
        return usExternalSourceStatusService.status(ticker);
    }

    @GetMapping("/{ticker}/valuation-diagnostics")
    public UsValuationDiagnosticsService.ValuationDiagnostics valuationDiagnostics(@PathVariable String ticker) {
        return usValuationDiagnosticsService.diagnostics(ticker);
    }

    @GetMapping("/valuation-jobs")
    public List<UsValuationJobRecord> valuationJobs(@RequestParam(defaultValue = "20") int limit) {
        return usValuationJobService.recentJobs(Math.max(1, Math.min(limit, 100)));
    }

    @GetMapping("/valuation-jobs/dead-letter")
    public List<UsValuationJobRecord> deadLetterJobs(@RequestParam(defaultValue = "20") int limit) {
        return usValuationJobService.deadLetterJobs(Math.max(1, Math.min(limit, 100)));
    }

    @GetMapping("/valuation-jobs/summary")
    public UsValuationJobSummary valuationJobSummary(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "2") int maxRetries
    ) {
        return usValuationJobService.summary(Math.max(1, hours), Math.max(1, maxRetries));
    }

    @GetMapping("/ranking-coverage")
    public UsRankingCoverageMetrics rankingCoverage() {
        return marketDiscoveryService.usRankingCoverage();
    }

    @GetMapping("/valuation-alerts")
    public UsValuationAlertService.AlertSummary valuationAlerts() {
        return usValuationAlertService.summarize();
    }

    @GetMapping("/{ticker}/valuation-jobs")
    public List<UsValuationJobRecord> valuationJobsForTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return usValuationJobService.recentJobsForTicker(ticker, Math.max(1, Math.min(limit, 100)));
    }

    @PostMapping("/valuation-jobs/retry-failed")
    public List<UsValuationJobService.JobExecutionResult> retryFailedJobs(
            @RequestParam(defaultValue = "2") int maxRetries,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return usValuationJobService.retryFailedJobs(
                Math.max(1, maxRetries),
                Math.max(1, hours),
                Math.max(1, Math.min(limit, 100))
        );
    }

    @PostMapping("/{ticker}/valuation-jobs/retry-failed")
    public List<UsValuationJobService.JobExecutionResult> retryFailedJobsForTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "2") int maxRetries,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return usValuationJobService.retryFailedJobsForTicker(
                ticker,
                Math.max(1, maxRetries),
                Math.max(1, hours),
                Math.max(1, Math.min(limit, 100))
        );
    }

    @PostMapping("/valuation-jobs/recover-stale")
    public List<UsValuationJobRecord> recoverStaleJobs(
            @RequestParam(defaultValue = "90") int staleMinutes,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return usValuationJobService.recoverStaleRunningJobs(
                Math.max(1, staleMinutes),
                Math.max(1, Math.min(limit, 100))
        );
    }

    @PostMapping("/snapshot-backfill/top50")
    public UsSnapshotBackfillService.SnapshotBackfillSummary backfillTop50() {
        return usSnapshotBackfillService.backfillTop50();
    }

    @PostMapping("/snapshot-backfill/universe")
    public UsSnapshotBackfillService.SnapshotBackfillSummary backfillUniversePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "queue") String mode,
            @RequestParam(defaultValue = "180") int priority
    ) {
        return "run".equalsIgnoreCase(mode)
                ? usSnapshotBackfillService.backfillUniversePage(page, size)
                : usSnapshotBackfillService.queueUniversePage(page, size, priority);
    }

    @PostMapping("/universe/sync")
    public UsUniverseSyncService.UniverseSyncSummary syncUniverse() {
        return usUniverseSyncService.syncSecTickerUniverse();
    }

    @PostMapping("/{ticker}/sec-sync")
    public UsSecDocumentSyncService.SecDocumentSyncSummary syncTicker(@PathVariable String ticker) {
        return usSecDocumentSyncService.syncTicker(ticker);
    }

    @PostMapping("/{ticker}/ir-sync")
    public UsCompanyIrSyncService.CompanyIrSyncSummary syncCompanyIr(@PathVariable String ticker) {
        return usCompanyIrSyncService.syncTicker(ticker);
    }

    @PostMapping("/{ticker}/market-sync")
    public UsEquityAdminOverviewService.AdminOverview syncMarket(@PathVariable String ticker) {
        marketDataService.getSnapshot(Market.US, ticker);
        return usEquityAdminOverviewService.overview(ticker);
    }

    @PostMapping("/{ticker}/standardize")
    public UsFinancialStandardizationService.StandardizationSummary standardize(@PathVariable String ticker) {
        return usFinancialStandardizationService.standardizeTicker(ticker);
    }
}
