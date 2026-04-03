package com.fairvalue.engine.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
    void shouldReturnDiscoveryForAllMarkets(String market) throws Exception {
        mockMvc.perform(get("/v1/discovery/{market}", market)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "CN", "JP", "HK"})
    void shouldReturnUndervaluedRankingsForAllMarkets(String market) throws Exception {
        mockMvc.perform(get("/v1/rankings/{market}/undervalued", market)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value(market))
                .andExpect(jsonPath("$.ranking_type").value("undervalued"))
                .andExpect(jsonPath("$.generated_at").exists())
                .andExpect(jsonPath("$.data_as_of").exists())
                .andExpect(jsonPath("$.ranking_basis").value("upside_pct"))
                .andExpect(jsonPath("$.items").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "CN", "JP", "HK"})
    void shouldReturnOvervaluedRankingsForAllMarkets(String market) throws Exception {
        mockMvc.perform(get("/v1/rankings/{market}/overvalued", market)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value(market))
                .andExpect(jsonPath("$.ranking_type").value("overvalued"))
                .andExpect(jsonPath("$.items").isArray());
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUsRankingsWithoutCnSpecificFields() throws Exception {
        mockMvc.perform(get("/v1/rankings/US/undervalued")
                        .param("page", "1")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].verdict").exists())
                .andExpect(jsonPath("$.items[0].cashflow_rating").doesNotExist())
                .andExpect(jsonPath("$.items[0].growth_rating").doesNotExist())
                .andExpect(jsonPath("$.items[0].financial_health").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "CN", "JP", "HK"})
    void shouldReturnPeersForAllMarkets(String market) throws Exception {
        String symbol = switch (market) {
            case "US", "JP" -> "AAPL";
            case "CN" -> "601988";
            case "HK" -> "0700.HK";
            default -> "AAPL";
        };

        mockMvc.perform(get("/v1/peers/{market}/{symbol}", market, symbol)
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value(market))
                .andExpect(jsonPath("$.symbol").value(symbol.toUpperCase()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.selection_basis").exists());
    }

    @org.junit.jupiter.api.Test
    void shouldReturnUsPeersWithoutTemplateOnlyFallback() throws Exception {
        mockMvc.perform(get("/v1/peers/US/AAPL").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.items.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.source_mode").value(not("template_only")))
                .andExpect(jsonPath("$.items[0].verdict").value(matchesPattern("^(UNDERVALUED|OVERVALUED|FAIR)$")));
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
