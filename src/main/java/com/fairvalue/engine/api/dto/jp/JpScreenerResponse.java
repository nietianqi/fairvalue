package com.fairvalue.engine.api.dto.jp;

import java.util.List;

public record JpScreenerResponse(
        int count,
        List<JpScreenerItem> items
) {
}
