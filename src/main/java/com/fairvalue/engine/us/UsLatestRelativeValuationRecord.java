package com.fairvalue.engine.us;

import java.time.Instant;

public record UsLatestRelativeValuationRecord(
        long valuationRunId,
        Instant valuationDate,
        String assumptionsJson
) {
}
