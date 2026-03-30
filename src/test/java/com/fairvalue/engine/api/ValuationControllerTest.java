package com.fairvalue.engine.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValuationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"US", "CN", "JP", "HK"})
    void shouldReturnValuationForAllMarkets(String market) throws Exception {
        mockMvc.perform(get("/v1/valuation/{market}/{symbol}", market, market.equals("HK") ? "0700.HK" : "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value(market))
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.models[0].name").exists())
                .andExpect(jsonPath("$.tradable_fair_value").isNumber());
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "CN", "JP", "HK"})
    void shouldReturnExplainForAllMarkets(String market) throws Exception {
        mockMvc.perform(get("/v1/valuation/{market}/{symbol}/explain", market, market.equals("HK") ? "0700.HK" : "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value(market))
                .andExpect(jsonPath("$.model_selection_reason").exists())
                .andExpect(jsonPath("$.confidence_breakdown.final_confidence").exists());
    }

    @org.junit.jupiter.api.Test
    void shouldReturnScenario() throws Exception {
        String payload = """
                {
                  "market": "US",
                  "symbol": "AAPL",
                  "revenueGrowth": 0.1,
                  "wacc": 0.085,
                  "terminalGrowth": 0.028,
                  "targetPe": 31
                }
                """;

        mockMvc.perform(post("/v1/valuation/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios[0].name").value("pessimistic"))
                .andExpect(jsonPath("$.scenarios[2].name").value("optimistic"));
    }
}
