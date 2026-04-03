package com.fairvalue.engine.api.dto.jp;

import java.util.List;

public record JpRecalcRequest(
        List<String> codes,
        boolean force
) {
}
