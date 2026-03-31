package com.fairvalue.engine.us;

import java.math.BigDecimal;

public record UsRelativePeerComparable(
        long securityId,
        String ticker,
        String companyName,
        String sector,
        String industry,
        String companyType,
        String sectorTemplate,
        BigDecimal lastPrice,
        BigDecimal marketCap,
        BigDecimal peTtm,
        BigDecimal pb,
        BigDecimal roic,
        BigDecimal fcfMargin,
        BigDecimal ebitda,
        BigDecimal netDebt
) {
}
