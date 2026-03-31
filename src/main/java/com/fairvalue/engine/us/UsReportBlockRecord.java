package com.fairvalue.engine.us;

public record UsReportBlockRecord(
        long valuationRunId,
        String blockType,
        String blockContentJson,
        int displayOrder
) {
}
