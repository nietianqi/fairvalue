package com.fairvalue.engine.api;

import com.fairvalue.engine.api.dto.cn.CnDiscoveryResponse;
import com.fairvalue.engine.api.dto.cn.CnRulesVersionResponse;
import com.fairvalue.engine.api.dto.cn.CnValuationReportResponse;
import com.fairvalue.engine.api.dto.cn.CnValuationRunRequest;
import com.fairvalue.engine.api.dto.cn.CnValuationRunResponse;
import com.fairvalue.engine.api.dto.cn.CnValuationSummaryResponse;
import com.fairvalue.engine.cn.CnStockValuationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cn-equities")
public class CnEquityController {
    private final CnStockValuationService cnStockValuationService;

    public CnEquityController(CnStockValuationService cnStockValuationService) {
        this.cnStockValuationService = cnStockValuationService;
    }

    @PostMapping("/{ticker}/valuation/run")
    public CnValuationRunResponse run(
            @PathVariable String ticker,
            @Valid @RequestBody(required = false) CnValuationRunRequest request
    ) {
        return cnStockValuationService.run(ticker, request);
    }

    @GetMapping("/{ticker}/valuation/summary")
    public CnValuationSummaryResponse summary(@PathVariable String ticker) {
        return cnStockValuationService.summary(ticker, null);
    }

    @GetMapping("/{ticker}/valuation/report")
    public CnValuationReportResponse report(@PathVariable String ticker) {
        return cnStockValuationService.report(ticker, null);
    }

    @GetMapping("/discovery")
    public CnDiscoveryResponse discovery(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer size
    ) {
        return cnStockValuationService.discovery(page, size);
    }

    @GetMapping("/rules/version")
    public CnRulesVersionResponse rulesVersion() {
        return cnStockValuationService.rulesVersion();
    }
}
