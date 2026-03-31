package com.fairvalue.engine.api;

import com.fairvalue.engine.us.UsEquityAdminOverviewService;
import com.fairvalue.engine.us.UsCompanyIrSyncService;
import com.fairvalue.engine.us.UsFinancialStandardizationService;
import com.fairvalue.engine.us.UsSecDocumentSyncService;
import com.fairvalue.engine.us.UsUniverseSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/us-equities-admin")
public class UsEquityAdminController {
    private final UsUniverseSyncService usUniverseSyncService;
    private final UsSecDocumentSyncService usSecDocumentSyncService;
    private final UsCompanyIrSyncService usCompanyIrSyncService;
    private final UsFinancialStandardizationService usFinancialStandardizationService;
    private final UsEquityAdminOverviewService usEquityAdminOverviewService;

    public UsEquityAdminController(
            UsUniverseSyncService usUniverseSyncService,
            UsSecDocumentSyncService usSecDocumentSyncService,
            UsCompanyIrSyncService usCompanyIrSyncService,
            UsFinancialStandardizationService usFinancialStandardizationService,
            UsEquityAdminOverviewService usEquityAdminOverviewService
    ) {
        this.usUniverseSyncService = usUniverseSyncService;
        this.usSecDocumentSyncService = usSecDocumentSyncService;
        this.usCompanyIrSyncService = usCompanyIrSyncService;
        this.usFinancialStandardizationService = usFinancialStandardizationService;
        this.usEquityAdminOverviewService = usEquityAdminOverviewService;
    }

    @GetMapping("/{ticker}/overview")
    public UsEquityAdminOverviewService.AdminOverview overview(@PathVariable String ticker) {
        return usEquityAdminOverviewService.overview(ticker);
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

    @PostMapping("/{ticker}/standardize")
    public UsFinancialStandardizationService.StandardizationSummary standardize(@PathVariable String ticker) {
        return usFinancialStandardizationService.standardizeTicker(ticker);
    }
}
