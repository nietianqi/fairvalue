package com.fairvalue.engine.api.cn;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
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
class CnEquityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeRulesVersion() throws Exception {
        mockMvc.perform(get("/v1/cn-equities/rules/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cn_stock_valuation_engine"))
                .andExpect(jsonPath("$.minimum_model_count").value(Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void shouldExposeDiscoveryFeed() throws Exception {
        mockMvc.perform(get("/v1/cn-equities/discovery").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].ticker").exists())
                .andExpect(jsonPath("$.items[0].fair_value").isNumber());
    }

    @Test
    void shouldRunCnValuation() throws Exception {
        String payload = """
                {
                  "market": "CN",
                  "currency": "CNY",
                  "outputMode": "full",
                  "overrideAssumptions": {
                    "wacc": 0.10,
                    "terminal_growth": 0.022
                  }
                }
                """;

        mockMvc.perform(post("/v1/cn-equities/600519/valuation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company_type").exists())
                .andExpect(jsonPath("$.method_results.length()").value(Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.scenarios.length()").value(3))
                .andExpect(jsonPath("$.operation_zones.zones.length()").value(5));
    }

    @Test
    void shouldReturnSummaryAndReport() throws Exception {
        mockMvc.perform(get("/v1/cn-equities/600519/valuation/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").exists())
                .andExpect(jsonPath("$.fair_value_mid").isNumber());

        mockMvc.perform(get("/v1/cn-equities/600519/valuation/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.company_and_industry_positioning").exists())
                .andExpect(jsonPath("$.valuation_methods.length()").value(Matchers.greaterThanOrEqualTo(3)));
    }
}
