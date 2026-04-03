package com.fairvalue.engine.api.dto.cn;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public record CnValuationRunRequest(
        @JsonAlias("market") String market,
        @JsonAlias("currency") String currency,
        @JsonAlias({"overrideAssumptions", "override_assumptions"}) Map<String, Double> overrideAssumptions,
        @JsonAlias({"outputMode", "output_mode"}) String outputMode
) {
}
