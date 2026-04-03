package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsValuationRunRequest;
import org.springframework.stereotype.Service;

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

    public UsSnapshotBackfillService(
            UsEquityValuationService usEquityValuationService,
            UsSecurityMasterService usSecurityMasterService
    ) {
        this.usEquityValuationService = usEquityValuationService;
        this.usSecurityMasterService = usSecurityMasterService;
    }

    public SnapshotBackfillSummary backfillTop50() {
        List<SnapshotBackfillItem> results = new ArrayList<>();
        int completed = 0;
        int missing = 0;
        int failed = 0;
        for (String ticker : TOP50_TICKERS) {
            String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
            if (usSecurityMasterService.resolveSecurityId(normalizedTicker).isEmpty()) {
                missing++;
                results.add(new SnapshotBackfillItem(normalizedTicker, "missing_in_security_master", "Ticker is not present in security_master."));
                continue;
            }
            try {
                usEquityValuationService.runValuation(normalizedTicker, UsValuationRunRequest.defaults());
                completed++;
                results.add(new SnapshotBackfillItem(normalizedTicker, "completed", null));
            } catch (Exception ex) {
                failed++;
                results.add(new SnapshotBackfillItem(normalizedTicker, "failed", ex.getMessage()));
            }
        }
        return new SnapshotBackfillSummary(
                "top50_cross_industry_v1",
                TOP50_TICKERS.size(),
                completed,
                missing,
                failed,
                results
        );
    }

    public record SnapshotBackfillSummary(
            String listName,
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
