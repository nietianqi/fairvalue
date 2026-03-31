package com.fairvalue.engine.api.dto.us;

public record UsExplanationBlock(
        String key,
        String title,
        String content,
        int displayOrder
) {
}
