package com.fairvalue.engine.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.platform.ApiClientRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ApiClientRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ApiClientRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Optional<ApiClientRecord> findEnabledByKey(String clientKey) {
        return jdbcClient.sql("""
                        SELECT id,
                               client_key,
                               client_name,
                               plan_code,
                               enabled,
                               public_client,
                               requests_per_minute,
                               daily_quota,
                               entitlements_json::text AS entitlements_json,
                               metadata_json::text AS metadata_json
                        FROM fairvalue.api_clients
                        WHERE client_key = :clientKey
                          AND enabled = TRUE
                        """)
                .param("clientKey", clientKey)
                .query(this::mapRow)
                .optional();
    }

    public Optional<ApiClientRecord> findPublicClient() {
        return jdbcClient.sql("""
                        SELECT id,
                               client_key,
                               client_name,
                               plan_code,
                               enabled,
                               public_client,
                               requests_per_minute,
                               daily_quota,
                               entitlements_json::text AS entitlements_json,
                               metadata_json::text AS metadata_json
                        FROM fairvalue.api_clients
                        WHERE public_client = TRUE
                          AND enabled = TRUE
                        ORDER BY updated_at DESC, id DESC
                        LIMIT 1
                        """)
                .query(this::mapRow)
                .optional();
    }

    public List<ApiClientRecord> findAll() {
        return jdbcClient.sql("""
                        SELECT id,
                               client_key,
                               client_name,
                               plan_code,
                               enabled,
                               public_client,
                               requests_per_minute,
                               daily_quota,
                               entitlements_json::text AS entitlements_json,
                               metadata_json::text AS metadata_json
                        FROM fairvalue.api_clients
                        ORDER BY public_client DESC, updated_at DESC, id DESC
                        """)
                .query(this::mapRow)
                .list();
    }

    public Optional<ApiClientRecord> findById(long id) {
        return jdbcClient.sql("""
                        SELECT id,
                               client_key,
                               client_name,
                               plan_code,
                               enabled,
                               public_client,
                               requests_per_minute,
                               daily_quota,
                               entitlements_json::text AS entitlements_json,
                               metadata_json::text AS metadata_json
                        FROM fairvalue.api_clients
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    public ApiClientRecord insert(
            String clientKey,
            String clientName,
            String planCode,
            boolean publicClient,
            int requestsPerMinute,
            long dailyQuota,
            List<String> entitlements,
            Map<String, Object> metadata
    ) {
        return jdbcClient.sql("""
                        INSERT INTO fairvalue.api_clients (
                            client_key,
                            client_name,
                            plan_code,
                            enabled,
                            public_client,
                            requests_per_minute,
                            daily_quota,
                            entitlements_json,
                            metadata_json
                        ) VALUES (
                            :clientKey,
                            :clientName,
                            :planCode,
                            TRUE,
                            :publicClient,
                            :requestsPerMinute,
                            :dailyQuota,
                            CAST(:entitlementsJson AS jsonb),
                            CAST(:metadataJson AS jsonb)
                        )
                        RETURNING id,
                                  client_key,
                                  client_name,
                                  plan_code,
                                  enabled,
                                  public_client,
                                  requests_per_minute,
                                  daily_quota,
                                  entitlements_json::text AS entitlements_json,
                                  metadata_json::text AS metadata_json
                        """)
                .param("clientKey", clientKey)
                .param("clientName", clientName)
                .param("planCode", planCode)
                .param("publicClient", publicClient)
                .param("requestsPerMinute", requestsPerMinute)
                .param("dailyQuota", dailyQuota)
                .param("entitlementsJson", writeJson(entitlements))
                .param("metadataJson", writeJson(metadata))
                .query(this::mapRow)
                .single();
    }

    public ApiClientRecord rotateKey(long id, String newKey) {
        return jdbcClient.sql("""
                        UPDATE fairvalue.api_clients
                        SET client_key = :newKey,
                            updated_at = NOW(),
                            metadata_json = jsonb_set(
                                COALESCE(metadata_json, '{}'::jsonb),
                                '{rotated_at}',
                                to_jsonb(to_char(NOW(), 'YYYY-MM-DD\"T\"HH24:MI:SSOF'))
                            )
                        WHERE id = :id
                        RETURNING id,
                                  client_key,
                                  client_name,
                                  plan_code,
                                  enabled,
                                  public_client,
                                  requests_per_minute,
                                  daily_quota,
                                  entitlements_json::text AS entitlements_json,
                                  metadata_json::text AS metadata_json
                        """)
                .param("id", id)
                .param("newKey", newKey)
                .query(this::mapRow)
                .single();
    }

    public ApiClientRecord disable(long id) {
        return jdbcClient.sql("""
                        UPDATE fairvalue.api_clients
                        SET enabled = FALSE,
                            public_client = FALSE,
                            updated_at = NOW(),
                            metadata_json = jsonb_set(
                                COALESCE(metadata_json, '{}'::jsonb),
                                '{disabled_at}',
                                to_jsonb(to_char(NOW(), 'YYYY-MM-DD\"T\"HH24:MI:SSOF'))
                            )
                        WHERE id = :id
                        RETURNING id,
                                  client_key,
                                  client_name,
                                  plan_code,
                                  enabled,
                                  public_client,
                                  requests_per_minute,
                                  daily_quota,
                                  entitlements_json::text AS entitlements_json,
                                  metadata_json::text AS metadata_json
                        """)
                .param("id", id)
                .query(this::mapRow)
                .single();
    }

    private ApiClientRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ApiClientRecord(
                rs.getLong("id"),
                rs.getString("client_key"),
                rs.getString("client_name"),
                rs.getString("plan_code"),
                rs.getBoolean("enabled"),
                rs.getBoolean("public_client"),
                rs.getInt("requests_per_minute"),
                rs.getLong("daily_quota"),
                readList(rs.getString("entitlements_json")),
                readMap(rs.getString("metadata_json"))
        );
    }

    private List<String> readList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, STRING_OBJECT_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize API client JSON payload.", ex);
        }
    }
}
