package com.fairvalue.engine.valuation;

import java.time.LocalDate;

public record HistoryPoint(
        LocalDate date,
        double closePrice,
        double tradableFairValue,
        double deviation,
        LocalDate valuationRunDate,
        String runId,
        boolean simulated
) {
}
