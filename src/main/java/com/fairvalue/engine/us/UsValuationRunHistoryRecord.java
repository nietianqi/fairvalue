package com.fairvalue.engine.us;

import java.time.LocalDate;

public record UsValuationRunHistoryRecord(
        LocalDate valuationDate,
        LocalDate valuationRunDate,
        double fairValueMid,
        String runId
) {
}
