package com.fairvalue.engine.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BatchValuationItem(
        @NotBlank String market,
        @NotBlank String symbol
) {
}
