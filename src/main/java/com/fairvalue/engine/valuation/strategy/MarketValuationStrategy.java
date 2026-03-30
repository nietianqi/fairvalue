package com.fairvalue.engine.valuation.strategy;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.valuation.MarketComputation;

public interface MarketValuationStrategy {
    Market market();

    MarketComputation evaluate(StockSnapshot snapshot);
}
