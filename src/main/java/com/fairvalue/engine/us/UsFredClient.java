package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UsFredClient {
    private final RestClient apiRestClient;
    private final RestClient graphRestClient;
    private final ObjectMapper objectMapper;
    private final UsFredProperties properties;
    private final Map<String, CacheEntry<Optional<FredSeriesObservation>>> cache = new ConcurrentHashMap<>();

    public UsFredClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            UsFredProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis());
        this.apiRestClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("User-Agent", "FairvalueEngine/0.1")
                .build();
        this.graphRestClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.getGraphBaseUrl())
                .defaultHeader("User-Agent", "FairvalueEngine/0.1")
                .build();
    }

    public Optional<FredSeriesObservation> fetchRiskFreeRate() {
        return fetchLatestObservation(properties.getRiskFreeSeriesId());
    }

    public Optional<FredSeriesObservation> fetchPolicyRate() {
        return fetchLatestObservation(properties.getPolicyRateSeriesId());
    }

    public Optional<FredSeriesObservation> fetchLatestObservation(String seriesId) {
        if (!properties.isEnabled() || seriesId == null || seriesId.isBlank()) {
            return Optional.empty();
        }

        String normalized = seriesId.trim().toUpperCase();
        CacheEntry<Optional<FredSeriesObservation>> cached = cache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        Optional<FredSeriesObservation> loaded = loadObservation(normalized);
        cache.put(normalized, new CacheEntry<>(loaded, now.plus(Duration.ofSeconds(properties.getCacheTtlSeconds()))));
        return loaded;
    }

    private Optional<FredSeriesObservation> loadObservation(String seriesId) {
        Optional<FredSeriesObservation> apiValue = loadFromApi(seriesId);
        if (apiValue.isPresent()) {
            return apiValue;
        }
        return loadFromGraphCsv(seriesId);
    }

    private Optional<FredSeriesObservation> loadFromApi(String seriesId) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Optional.empty();
        }
        try {
            String body = apiRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fred/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", properties.getApiKey())
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", 12)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode node : root.path("observations")) {
                Double value = parsePercentValue(node.path("value").asText(null));
                if (value == null) {
                    continue;
                }
                LocalDate date = parseDate(node.path("date").asText(null));
                return Optional.of(new FredSeriesObservation(seriesId, date, value, "fred_api"));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<FredSeriesObservation> loadFromGraphCsv(String seriesId) {
        try {
            String csv = graphRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/graph/fredgraph.csv")
                            .queryParam("id", seriesId)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (csv == null || csv.isBlank()) {
                return Optional.empty();
            }

            String[] lines = csv.split("\\R");
            for (int i = lines.length - 1; i >= 1; i -= 1) {
                String[] parts = lines[i].split(",", -1);
                if (parts.length < 2) {
                    continue;
                }
                Double value = parsePercentValue(parts[1]);
                if (value == null) {
                    continue;
                }
                LocalDate date = parseDate(parts[0]);
                return Optional.of(new FredSeriesObservation(seriesId, date, value, "fred_graph_csv"));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Double parsePercentValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank() || ".".equals(trimmed)) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed) / 100.0;
        } catch (Exception ex) {
            return null;
        }
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    public record FredSeriesObservation(
            String seriesId,
            LocalDate date,
            double value,
            String sourceLabel
    ) {
    }
}
