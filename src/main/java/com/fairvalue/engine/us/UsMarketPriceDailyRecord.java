package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsMarketPriceDailyRecord(
        LocalDate tradeDate,
        BigDecimal close
) {
}
