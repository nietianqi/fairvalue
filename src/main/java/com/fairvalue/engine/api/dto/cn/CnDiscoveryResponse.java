package com.fairvalue.engine.api.dto.cn;

import java.util.List;

public record CnDiscoveryResponse(
        int page,
        int size,
        long total,
        String source,
        List<CnDiscoveryItem> items
) {
}
