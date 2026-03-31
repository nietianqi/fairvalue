package com.fairvalue.engine.api;

import com.fairvalue.engine.api.dto.us.UsDataQualityResponse;
import com.fairvalue.engine.api.dto.us.UsEquityProfileResponse;
import com.fairvalue.engine.api.dto.us.UsFinancialQualityResponse;
import com.fairvalue.engine.api.dto.us.UsValuationReportResponse;
import com.fairvalue.engine.api.dto.us.UsValuationRunRequest;
import com.fairvalue.engine.api.dto.us.UsValuationRunResponse;
import com.fairvalue.engine.api.dto.us.UsValuationSummaryResponse;
import com.fairvalue.engine.us.UsEquityValuationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/us-equities")
public class UsEquityController {
    private final UsEquityValuationService usEquityValuationService;

    public UsEquityController(UsEquityValuationService usEquityValuationService) {
        this.usEquityValuationService = usEquityValuationService;
    }

    @GetMapping("/{ticker}/profile")
    public UsEquityProfileResponse profile(@PathVariable String ticker) {
        return usEquityValuationService.profile(ticker);
    }

    @GetMapping("/{ticker}/data-quality")
    public UsDataQualityResponse dataQuality(@PathVariable String ticker) {
        return usEquityValuationService.dataQuality(ticker);
    }

    @GetMapping("/{ticker}/financial-quality")
    public UsFinancialQualityResponse financialQuality(@PathVariable String ticker) {
        return usEquityValuationService.financialQuality(ticker);
    }

    @PostMapping("/{ticker}/valuation/run")
    public UsValuationRunResponse run(
            @PathVariable String ticker,
            @Valid @RequestBody(required = false) UsValuationRunRequest request
    ) {
        return usEquityValuationService.runValuation(ticker, request);
    }

    @GetMapping("/{ticker}/valuation/summary")
    public UsValuationSummaryResponse summary(@PathVariable String ticker) {
        return usEquityValuationService.summary(ticker);
    }

    @GetMapping("/{ticker}/valuation/report")
    public UsValuationReportResponse report(@PathVariable String ticker) {
        return usEquityValuationService.report(ticker);
    }
}
