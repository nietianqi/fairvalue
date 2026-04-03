package com.fairvalue.engine.api.dto.jp;

import java.util.List;

public record JpEventsResponse(
        String code,
        List<JpEventItem> events
) {
}
