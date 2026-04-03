package com.fairvalue.engine.api.dto.jp;

import java.util.List;

public record JpRecalcResponse(
        String runId,
        int requested,
        int recalculated,
        List<String> codes
) {
}
