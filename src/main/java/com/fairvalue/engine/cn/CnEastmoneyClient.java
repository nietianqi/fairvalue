package com.fairvalue.engine.cn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CnEastmoneyClient {
    private static final String QUOTE_UT = "fa5fd1943c7b386f172d6893dbfba10b";
    private static final String UNIVERSE_FS = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CnLiveMarketDataProperties properties;
    private final Map<String, CacheEntry<Optional<CnLiveQuote>>> quoteCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<CnUniversePage>> universeCache = new ConcurrentHashMap<>();

    public CnEastmoneyClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            CnLiveMarketDataProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Referer", "https://quote.eastmoney.com/")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Optional<CnLiveQuote> fetchQuote(String symbol) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String normalized = normalizeSymbol(symbol);
        return cached(
                quoteCache,
                normalized,
                Duration.ofSeconds(properties.getQuoteCacheTtlSeconds()),
                () -> loadQuote(normalized)
        );
    }

    public CnUniversePage fetchUniversePage(Integer page, Integer size) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int requestedSize = size == null || size < 1 ? properties.getDefaultPageSize() : size;
        int resolvedSize = Math.min(requestedSize, properties.getMaxPageSize());

        if (!properties.isEnabled()) {
            return new CnUniversePage(resolvedPage, resolvedSize, 0, List.of(), "fallback-disabled");
        }

        String cacheKey = resolvedPage + ":" + resolvedSize;
        return cached(
                universeCache,
                cacheKey,
                Duration.ofSeconds(properties.getUniverseCacheTtlSeconds()),
                () -> loadUniversePage(resolvedPage, resolvedSize)
        );
    }

    private Optional<CnLiveQuote> loadQuote(String symbol) {
        for (String secid : candidateSecIds(symbol)) {
            try {
                String body = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/qt/stock/get")
                                .queryParam("ut", QUOTE_UT)
                                .queryParam("fltt", "2")
                                .queryParam("invt", "2")
                                .queryParam("fields", "f57,f58,f127,f43,f116,f162,f167,f173")
                                .queryParam("secid", secid)
                                .build())
                        .retrieve()
                        .body(String.class);

                JsonNode data = objectMapper.readTree(body).path("data");
                if (data.isMissingNode() || data.isNull() || text(data, "f57").isBlank()) {
                    continue;
                }

                return Optional.of(new CnLiveQuote(
                        text(data, "f57"),
                        text(data, "f58"),
                        text(data, "f127"),
                        positive(number(data, "f43")),
                        nullableNumber(data, "f162"),
                        nullableNumber(data, "f167"),
                        ratio(nullableNumber(data, "f173")),
                        nullableNumber(data, "f116"),
                        secid.startsWith("1.") ? "SH" : "SZ",
                        "eastmoney-live:" + LocalDate.now()
                ));
            } catch (Exception ignored) {
                // Try the next secid candidate and fall back gracefully.
            }
        }
        return Optional.empty();
    }

    private CnUniversePage loadUniversePage(int page, int size) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/qt/clist/get")
                            .queryParam("pn", page)
                            .queryParam("pz", size)
                            .queryParam("po", "1")
                            .queryParam("np", "1")
                            .queryParam("fltt", "2")
                            .queryParam("invt", "2")
                            .queryParam("fid", "f6")
                            .queryParam("fs", UNIVERSE_FS)
                            .queryParam("fields", "f2,f3,f5,f6,f8,f9,f12,f13,f14,f20,f23,f24")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode data = objectMapper.readTree(body).path("data");
            long total = data.path("total").asLong(0);
            List<CnUniverseItem> items = new ArrayList<>();
            for (JsonNode node : data.path("diff")) {
                double price = positive(number(node, "f2"));
                String symbol = text(node, "f12");
                String name = text(node, "f14");
                if (price <= 0 || symbol.isBlank() || name.isBlank()) {
                    continue;
                }

                items.add(new CnUniverseItem(
                        symbol,
                        node.path("f13").asInt() == 1 ? "SH" : "SZ",
                        name,
                        price,
                        ratio(nullableNumber(node, "f3")),
                        nullableNumber(node, "f6"),
                        ratio(nullableNumber(node, "f8")),
                        nullableNumber(node, "f9"),
                        nullableNumber(node, "f23"),
                        nullableNumber(node, "f20"),
                        ratio(nullableNumber(node, "f24")),
                        "eastmoney-universe:" + LocalDate.now()
                ));
            }
            items.sort(Comparator.comparingDouble(CnUniverseItem::turnoverAmount).reversed());
            return new CnUniversePage(page, size, total, items, "eastmoney");
        } catch (Exception ignored) {
            return new CnUniversePage(page, size, 0, List.of(), "fallback-error");
        }
    }

    private List<String> candidateSecIds(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.matches("^(600|601|603|605|688|689|900)\\d*$")) {
            return List.of("1." + normalized);
        }
        if (normalized.matches("^(000|001|002|003|200|300|301)\\d*$")) {
            return List.of("0." + normalized);
        }
        return List.of("1." + normalized, "0." + normalized);
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : value.asDouble(0.0);
    }

    private Double nullableNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        double resolved = value.isNumber() ? value.asDouble() : value.asDouble(Double.NaN);
        return Double.isFinite(resolved) ? resolved : null;
    }

    private double positive(double value) {
        return value > 0 ? value : 0.0;
    }

    private Double ratio(Double percent) {
        if (percent == null) {
            return null;
        }
        return percent / 100.0;
    }

    private <T> T cached(
            Map<String, CacheEntry<T>> cache,
            String key,
            Duration ttl,
            Loader<T> loader
    ) {
        CacheEntry<T> cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        T loaded = loader.load();
        cache.put(key, new CacheEntry<>(loaded, now.plus(ttl)));
        return loaded;
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    @FunctionalInterface
    private interface Loader<T> {
        T load();
    }

    public record CnLiveQuote(
            String symbol,
            String name,
            String industry,
            double price,
            Double pe,
            Double pb,
            Double roe,
            Double marketCap,
            String exchange,
            String dataVersion
    ) {
    }

    public record CnUniverseItem(
            String symbol,
            String exchange,
            String name,
            double price,
            Double changePercent,
            Double turnoverAmount,
            Double turnoverRate,
            Double pe,
            Double pb,
            Double marketCap,
            Double sixtyDayChange,
            String dataVersion
    ) {
    }

    public record CnUniversePage(
            int page,
            int size,
            long total,
            List<CnUniverseItem> items,
            String source
    ) {
    }
}
