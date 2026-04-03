package com.fairvalue.engine.api.dto.jp;

import java.util.List;

public record JpHistoryResponse(
        String code,
        List<JpHistoryPoint> history
) {
}
