package com.fairvalue.engine.us;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UsStooqClient {
    private static final Logger log = LoggerFactory.getLogger(UsStooqClient.class);
    private static final DateTimeFormatter STOOQ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final UsLiveMarketDataProperties properties;
    private final Map<String, CacheEntry<Optional<UsQuote>>> quoteCache = new ConcurrentHashMap<>();

    public UsStooqClient(RestClient.Builder restClientBuilder, UsLiveMarketDataProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getStooqBaseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    public Optional<UsQuote> fetchQuote(String ticker) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String normalized = normalizeTicker(ticker);
        CacheEntry<Optional<UsQuote>> cached = quoteCache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        Optional<UsQuote> loaded = loadQuote(normalized);
        quoteCache.put(normalized, new CacheEntry<>(loaded, now.plus(Duration.ofSeconds(properties.getQuoteCacheTtlSeconds()))));
        return loaded;
    }

    private Optional<UsQuote> loadQuote(String ticker) {
        try {
            String symbol = ticker.toLowerCase(Locale.ROOT).replace("-", ".") + ".us";
            String csv = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/q/l/")
                            .queryParam("s", symbol)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (csv == null || csv.isBlank()) {
                log.warn("[stooq] empty response for {}", symbol);
                return Optional.empty();
            }

            // Rate-limit detection: Stooq returns HTML/text "Exceeded the daily hits limit"
            if (csv.contains("Exceeded") || csv.contains("hits limit") || csv.startsWith("<")) {
                log.warn("[stooq] rate-limited for {} — response: {}", symbol, csv.substring(0, Math.min(80, csv.length())));
                return Optional.empty();
            }

            String[] parts = csv.trim().split(",");
            if (parts.length < 7 || "N/D".equalsIgnoreCase(parts[1])) {
                log.debug("[stooq] no data (N/D or short response) for {}: {}", symbol, csv.substring(0, Math.min(60, csv.length())));
                return Optional.empty();
            }

            int offset = hasTimeColumn(parts) ? 1 : 0;
            return Optional.of(new UsQuote(
                    ticker,
                    parseDate(parts[1]),
                    parseDouble(parts[2 + offset], 0.0),
                    parseDouble(parts[3 + offset], 0.0),
                    parseDouble(parts[4 + offset], 0.0),
                    parseDouble(parts[5 + offset], 0.0),
                    0.0,
                    parseLong(parts[6 + offset], 0L)
            ));
        } catch (Exception e) {
            log.warn("[stooq] fetch failed for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    private String normalizeTicker(String ticker) {
        return ticker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".", "-");
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return value.contains("-")
                    ? LocalDate.parse(value)
                    : LocalDate.parse(value, STOOQ_DATE);
        } catch (Exception ex) {
            return LocalDate.now();
        }
    }

    private boolean hasTimeColumn(String[] parts) {
        return parts.length >= 8 && parts[2] != null && parts[2].matches("^\\d{6}$");
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    public record UsQuote(
            String ticker,
            LocalDate tradeDate,
            double open,
            double high,
            double low,
            double close,
            double previousClose,
            long volume
    ) {
        public double dailyChange() {
            if (previousClose > 0) {
                return (close / previousClose) - 1.0;
            }
            if (open > 0) {
                return (close / open) - 1.0;
            }
            return 0.0;
        }
    }
}
