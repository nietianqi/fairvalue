package com.fairvalue.engine.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.util.List;

public record ScreenerRequest(
        List<String> markets,
        @DecimalMin("0") @DecimalMax("1") Double minUndervalued,
        @DecimalMin("0") Double minRoe,
        Boolean requirePositiveFcf,
        @DecimalMin("0") Double maxPb,
        @DecimalMin("0") Double minDividendYield,
        @Min(1) Integer limit
) {
}
