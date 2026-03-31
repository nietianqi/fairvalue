package com.fairvalue.engine.us;

import java.math.BigDecimal;
import java.time.Instant;

public record UsMarketSnapshotRecord(
        Instant snapshotTime,
        BigDecimal lastPrice,
        BigDecimal marketCapVendor,
        BigDecimal peTtmVendor,
        BigDecimal pbVendor,
        BigDecimal dividendYieldVendor
) {
}
