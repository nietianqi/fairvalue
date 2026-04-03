package com.fairvalue.engine.api.jp;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JpEquityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnOverviewAndFairValue() throws Exception {
        mockMvc.perform(get("/v1/jp-equities/7203/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("7203"))
                .andExpect(jsonPath("$.fair_value_mid").isNumber());

        mockMvc.perform(get("/v1/jp-equities/7203/fair-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods_used.length()").value(Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.method_results.length()").value(Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.confidence_score").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldReturnHistoryEventsAndRecalc() throws Exception {
        mockMvc.perform(get("/v1/jp-equities/7203/history?days=120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(Matchers.greaterThanOrEqualTo(30)));

        mockMvc.perform(get("/v1/jp-equities/7203/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(Matchers.greaterThanOrEqualTo(1)));

        String recalcPayload = """
                {
                  "codes": ["7203", "6758"],
                  "force": true
                }
                """;

        mockMvc.perform(post("/v1/jp-equities/recalc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recalcPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.recalculated").value(2));
    }

    @Test
    void shouldScreenJpStocks() throws Exception {
        String payload = """
                {
                  "market": "JP",
                  "minUndervaluedPct": 0.0,
                  "minConfidenceScore": 40,
                  "minDividendYield": 0.0,
                  "maxPbr": 10,
                  "limit": 15
                }
                """;

        mockMvc.perform(post("/v1/jp-equities/screener")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].code").exists());
    }

    @Test
    void shouldRedirectLegacyGetEndpointsToCanonicalPaths() throws Exception {
        mockMvc.perform(get("/api/jp/stocks/7203/overview"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("/v1/jp-equities/7203/overview")));

        mockMvc.perform(get("/api/jp/stocks/7203/history?days=120"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("/v1/jp-equities/7203/history?days=120")));
    }
}
