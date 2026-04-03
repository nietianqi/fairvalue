package com.fairvalue.engine.api.dto.jp;

import java.time.LocalDate;

public record JpHistoryPoint(
        LocalDate date,
        double closePrice,
        double fairValueMid,
        double deviation,
        boolean simulated
) {
}
