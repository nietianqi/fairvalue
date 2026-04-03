package com.fairvalue.engine.api.dto.jp;

import java.time.LocalDate;

public record JpEventItem(
        LocalDate date,
        String type,
        String title,
        String expectedImpact
) {
}
