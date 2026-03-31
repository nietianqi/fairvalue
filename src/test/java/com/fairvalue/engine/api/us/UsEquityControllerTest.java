package com.fairvalue.engine.api.us;

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
class UsEquityControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnUsProfile() throws Exception {
        mockMvc.perform(get("/v1/us-equities/AAPL/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.sector_template").exists())
                .andExpect(jsonPath("$.company_type").exists());
    }

    @Test
    void shouldRunUsValuationWithMultiMethods() throws Exception {
        String payload = """
                {
                  "style": "balanced",
                  "horizon": "6-18m",
                  "useConsensus": true,
                  "forceMethods": ["dcf", "reverse_dcf", "ev_ebitda"],
                  "customAssumptions": {
                    "wacc": 0.085,
                    "terminal_growth": 0.028
                  }
                }
                """;

        mockMvc.perform(post("/v1/us-equities/AAPL/valuation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuation_methods.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.method_outputs.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.scenario_matrix.length()").value(3))
                .andExpect(jsonPath("$.risk_matrix.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void shouldReturnUsSummaryAndReport() throws Exception {
        mockMvc.perform(get("/v1/us-equities/AAPL/valuation/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buy_zone.low").exists())
                .andExpect(jsonPath("$.fair_value_range.mid").exists());

        mockMvc.perform(get("/v1/us-equities/AAPL/valuation/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.one_line_verdict").exists())
                .andExpect(jsonPath("$.valuation_breakdown.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }
}
