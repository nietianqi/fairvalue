package com.fairvalue.engine.api.dto.us;

import java.util.List;

public record UsValuationExplanationResponse(
        List<UsExplanationBlock> blocks
) {
}
