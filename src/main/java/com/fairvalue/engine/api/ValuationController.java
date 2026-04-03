package com.fairvalue.engine.api;

import com.fairvalue.engine.api.dto.BatchValuationRequest;
import com.fairvalue.engine.api.dto.BatchValuationResponse;
import com.fairvalue.engine.api.dto.HistoryResponse;
import com.fairvalue.engine.api.dto.MarketPeersResponse;
import com.fairvalue.engine.api.dto.MarketRankingResponse;
import com.fairvalue.engine.api.dto.ScenarioRequest;
import com.fairvalue.engine.api.dto.ScreenerRequest;
import com.fairvalue.engine.api.dto.ScreenerResponse;
import com.fairvalue.engine.api.dto.cn.CnDiscoveryResponse;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.service.MarketDiscoveryService;
import com.fairvalue.engine.service.ValuationService;
import com.fairvalue.engine.valuation.ExplainResult;
import com.fairvalue.engine.valuation.ScenarioResult;
import com.fairvalue.engine.valuation.ValuationResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class ValuationController {
    private final ValuationService valuationService;
    private final MarketDiscoveryService marketDiscoveryService;

    public ValuationController(
            ValuationService valuationService,
            MarketDiscoveryService marketDiscoveryService
    ) {
        this.valuationService = valuationService;
        this.marketDiscoveryService = marketDiscoveryService;
    }

    @GetMapping("/valuation/{market}/{symbol}")
    public ValuationResult getValuation(@PathVariable String market, @PathVariable String symbol) {
        return valuationService.valuate(Market.from(market), symbol);
    }

    @GetMapping("/discovery/{market}")
    public CnDiscoveryResponse discovery(
            @PathVariable String market,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer size
    ) {
        return marketDiscoveryService.discovery(Market.from(market), page, size);
    }

    @GetMapping("/rankings/{market}/undervalued")
    public MarketRankingResponse undervaluedRankings(
            @PathVariable String market,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer size
    ) {
        return marketDiscoveryService.rankings(Market.from(market), "undervalued", page, size);
    }

    @GetMapping("/rankings/{market}/overvalued")
    public MarketRankingResponse overvaluedRankings(
            @PathVariable String market,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "30") Integer size
    ) {
        return marketDiscoveryService.rankings(Market.from(market), "overvalued", page, size);
    }

    @GetMapping("/peers/{market}/{symbol}")
    public MarketPeersResponse peers(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return marketDiscoveryService.peers(Market.from(market), symbol, limit);
    }

    @PostMapping("/valuation/batch")
    public BatchValuationResponse batchValuation(@Valid @RequestBody BatchValuationRequest request) {
        List<ValuationService.MarketSymbol> items = request.items().stream()
                .map(item -> new ValuationService.MarketSymbol(Market.from(item.market()), item.symbol()))
                .toList();

        List<ValuationResult> results = valuationService.batchValuate(items);
        return new BatchValuationResponse(results.size(), results);
    }

    @GetMapping("/valuation/{market}/{symbol}/explain")
    public ExplainResult explain(@PathVariable String market, @PathVariable String symbol) {
        return valuationService.explain(Market.from(market), symbol);
    }

    @PostMapping("/valuation/scenario")
    public ScenarioResult scenario(@Valid @RequestBody ScenarioRequest request) {
        return valuationService.scenario(request);
    }

    @GetMapping("/valuation/history/{market}/{symbol}")
    public HistoryResponse history(
            @PathVariable String market,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "180") int days
    ) {
        Market resolvedMarket = Market.from(market);
        return new HistoryResponse(
                resolvedMarket.name(),
                symbol.toUpperCase(),
                valuationService.history(resolvedMarket, symbol, days)
        );
    }

    @PostMapping("/screener/valuation")
    public ScreenerResponse screener(@Valid @RequestBody(required = false) ScreenerRequest request) {
        ScreenerRequest payload = request == null
                ? new ScreenerRequest(null, null, null, null, null, null, null)
                : request;

        List<ValuationResult> candidates = valuationService.screener(payload);
        return new ScreenerResponse(candidates.size(), candidates);
    }
}
