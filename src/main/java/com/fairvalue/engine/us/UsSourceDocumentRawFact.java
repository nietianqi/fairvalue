package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsSourceDocumentRawFact(
        long sourceDocumentId,
        long securityId,
        String taxonomy,
        String conceptName,
        String unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer fiscalYear,
        String fiscalPeriod,
        BigDecimal valueNumeric,
        String valueText,
        Integer decimals,
        String contextRef,
        String segmentName,
        boolean customTag,
        String rawJson
) {
}
