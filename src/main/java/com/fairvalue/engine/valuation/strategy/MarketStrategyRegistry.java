package com.fairvalue.engine.valuation.strategy;

import com.fairvalue.engine.domain.Market;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MarketStrategyRegistry {
    private final Map<Market, MarketValuationStrategy> strategies;

    public MarketStrategyRegistry(List<MarketValuationStrategy> strategyList) {
        this.strategies = new EnumMap<>(Market.class);
        for (MarketValuationStrategy strategy : strategyList) {
            this.strategies.put(strategy.market(), strategy);
        }
    }

    public MarketValuationStrategy get(Market market) {
        MarketValuationStrategy strategy = strategies.get(market);
        if (strategy == null) {
            throw new IllegalStateException("No valuation strategy registered for market: " + market);
        }
        return strategy;
    }
}
