package com.fairvalue.engine.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchValuationRequest(
        @Valid @NotEmpty List<BatchValuationItem> items
) {
}
