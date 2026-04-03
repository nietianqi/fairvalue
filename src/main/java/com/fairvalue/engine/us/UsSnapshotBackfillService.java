package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsValuationRunRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UsSnapshotBackfillService {
    private static final List<String> TOP50_TICKERS = List.of(
            "AAPL", "MSFT", "NVDA", "GOOGL", "META", "AMZN", "AMD", "AVGO", "QCOM", "ADBE",
            "JPM", "BAC", "GS", "WFC", "MS",
            "JNJ", "UNH", "LLY", "ABBV", "PFE",
            "WMT", "COST", "MCD", "NKE", "HD",
            "CAT", "DE", "HON", "RTX", "GE",
            "XOM", "CVX", "COP", "SLB", "EOG",
            "NEE", "DUK", "SO", "AEP", "PCG",
            "LIN", "APD", "SHW", "NEM", "FCX",
            "PLD", "AMT", "EQIX", "SPG", "O"
    );

    private final UsEquityValuationService usEquityValuationService;
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsValuationJobService usValuationJobService;

    public UsSnapshotBackfillService(
            UsEquityValuationService usEquityValuationService,
            UsSecurityMasterService usSecurityMasterService,
            UsValuationJobService usValuationJobService
    ) {
        this.usEquityValuationService = usEquityValuationService;
        this.usSecurityMasterService = usSecurityMasterService;
        this.usValuationJobService = usValuationJobService;
    }

    public SnapshotBackfillSummary backfillTop50() {
        return processTickers("top50_cross_industry_v1", TOP50_TICKERS, "run", 0);
    }

    public SnapshotBackfillSummary backfillUniversePage(int page, int size) {
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.max(1, Math.min(size, 500));
        List<String> tickers = usSecurityMasterService.findActiveUsUniversePage(resolvedPage, resolvedSize).stream()
                .map(UsSecurityMaster::ticker)
                .toList();
        return processTickers("us_universe_page_%d_size_%d".formatted(resolvedPage, resolvedSize), tickers, "run", 0);
    }

    public SnapshotBackfillSummary queueUniversePage(int page, int size, int priority) {
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.max(1, Math.min(size, 500));
        int resolvedPriority = Math.max(priority, 100);
        List<String> tickers = usSecurityMasterService.findActiveUsUniversePage(resolvedPage, resolvedSize).stream()
                .map(UsSecurityMaster::ticker)
                .toList();
        return processTickers(
                "us_universe_page_%d_size_%d".formatted(resolvedPage, resolvedSize),
                tickers,
                "queue",
                resolvedPriority
        );
    }

    private SnapshotBackfillSummary processTickers(String listName, List<String> tickers, String mode, int priority) {
        List<SnapshotBackfillItem> results = new ArrayList<>();
        int completed = 0;
        int missing = 0;
        int failed = 0;
        for (String ticker : tickers) {
            String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
            if (usSecurityMasterService.resolveSecurityId(normalizedTicker).isEmpty()) {
                missing++;
                results.add(new SnapshotBackfillItem(normalizedTicker, "missing_in_security_master", "Ticker is not present in security_master."));
                continue;
            }
            try {
                if ("queue".equalsIgnoreCase(mode)) {
                    usValuationJobService.enqueueBulkRefresh(
                            normalizedTicker,
                            "admin_backfill",
                            priority,
                            Instant.now()
                    );
                    results.add(new SnapshotBackfillItem(normalizedTicker, "queued", "Queued with priority " + priority + "."));
                } else {
                    usEquityValuationService.runValuation(normalizedTicker, UsValuationRunRequest.defaults());
                    results.add(new SnapshotBackfillItem(normalizedTicker, "completed", null));
                }
                completed++;
            } catch (Exception ex) {
                failed++;
                results.add(new SnapshotBackfillItem(normalizedTicker, "failed", ex.getMessage()));
            }
        }
        return new SnapshotBackfillSummary(
                listName,
                mode.toLowerCase(Locale.ROOT),
                tickers.size(),
                completed,
                missing,
                failed,
                results
        );
    }

    public record SnapshotBackfillSummary(
            String listName,
            String mode,
            int requestedCount,
            int completedCount,
            int missingCount,
            int failedCount,
            List<SnapshotBackfillItem> items
    ) {
    }

    public record SnapshotBackfillItem(
            String ticker,
            String status,
            String detail
    ) {
    }
}
