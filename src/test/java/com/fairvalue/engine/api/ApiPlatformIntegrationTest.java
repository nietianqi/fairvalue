package com.fairvalue.engine.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "market-data.cn.live.enabled=false",
        "market-data.us.live.enabled=false",
        "app.valuation.us.schedule.enabled=false",
        "app.api.auth.enabled=true",
        "app.api.auth.valid-keys=alpha-key,beta-key",
        "app.api.rate-limit.enabled=true",
        "app.api.rate-limit.requests-per-minute=1",
        "app.api.envelope-enabled=true",
        "app.api.force-envelope=false"
})
@AutoConfigureMockMvc
class ApiPlatformIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectMissingApiKey() throws Exception {
        mockMvc.perform(get("/v1/discovery/US").param("page", "1").param("size", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void shouldWrapEnvelopeWhenOptedIn() throws Exception {
        mockMvc.perform(get("/v1/discovery/US")
                        .param("page", "1")
                        .param("size", "1")
                        .header("X-API-Key", "alpha-key")
                        .header("X-Use-Envelope", "true"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.request_id").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void shouldRateLimitPerApiKey() throws Exception {
        mockMvc.perform(get("/v1/discovery/US")
                        .param("page", "1")
                        .param("size", "1")
                        .header("X-API-Key", "beta-key"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/discovery/US")
                        .param("page", "1")
                        .param("size", "1")
                        .header("X-API-Key", "beta-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("rate_limited"));
    }

    @Test
    void shouldExposeBootstrapForFrontendClient() throws Exception {
        mockMvc.perform(get("/platform/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api_key_header").value("X-API-Key"))
                .andExpect(jsonPath("$.api_key").value("fv-browser-dev-key"))
                .andExpect(jsonPath("$.force_envelope").value(false))
                .andExpect(jsonPath("$.daily_quota").exists());
    }

    @Test
    void shouldExposePlatformUsageForSeededClient() throws Exception {
        mockMvc.perform(get("/v1/platform/me")
                        .header("X-API-Key", "fv-browser-dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_name").value("Fairvalue Browser Dev Client"))
                .andExpect(jsonPath("$.plan_code").value("developer_browser"));

        mockMvc.perform(get("/v1/platform/usage")
                        .header("X-API-Key", "fv-browser-dev-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_name").value("Fairvalue Browser Dev Client"))
                .andExpect(jsonPath("$.usage_date").exists())
                .andExpect(jsonPath("$.daily_quota").exists())
                .andExpect(jsonPath("$.remaining_quota").exists());
    }

    @Test
    void shouldManagePlatformClientsWithSeededAdminClient() throws Exception {
        mockMvc.perform(get("/v1/platform/clients")
                        .header("X-API-Key", "fv-service-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client_name").exists());

        mockMvc.perform(get("/v1/platform/plans")
                        .header("X-API-Key", "fv-service-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].daily_quota").exists());

        String created = mockMvc.perform(post("/v1/platform/clients")
                        .header("X-API-Key", "fv-service-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client_name": "Quota Test Client",
                                  "plan_code": "research_starter",
                                  "requests_per_minute": 10,
                                  "daily_quota": 1,
                                  "entitlements": ["market.read", "platform.read"],
                                  "metadata": {"owner": "test"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_name").value("Quota Test Client"))
                .andExpect(jsonPath("$.daily_quota").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode createdJson =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);
        long clientId = createdJson.get("id").asLong();
        String clientKey = createdJson.get("client_key").asText();

        String rotated = mockMvc.perform(post("/v1/platform/clients/{id}/rotate-key", clientId)
                        .header("X-API-Key", "fv-service-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.client_key").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rotatedJson =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(rotated);
        String rotatedKey = rotatedJson.get("client_key").asText();

        mockMvc.perform(get("/v1/platform/me")
                        .header("X-API-Key", rotatedKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_name").value("Quota Test Client"));

        mockMvc.perform(get("/v1/platform/usage")
                        .header("X-API-Key", rotatedKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("quota_exceeded"));

        mockMvc.perform(post("/v1/platform/clients/{id}/disable", clientId)
                        .header("X-API-Key", "fv-service-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
