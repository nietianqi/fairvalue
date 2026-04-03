package com.fairvalue.engine.us;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class UsSimfinClient {
    private final RestClient restClient;
    private final UsSimfinProperties properties;
    private final Map<String, CacheEntry<Object>> cache = new ConcurrentHashMap<>();

    public UsSimfinClient(
            RestClient.Builder restClientBuilder,
            UsSimfinProperties properties
    ) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis());
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("User-Agent", "FairvalueEngine/0.1")
                .build();
    }

    public SimfinStatus probe() {
        if (!properties.isEnabled()) {
            return new SimfinStatus(false, false, false, "disabled", null);
        }
        if (!properties.isConfigured()) {
            return new SimfinStatus(true, false, false, "unauthenticated", "api_key_missing");
        }
        return readCached("simfin:probe", SimfinStatus.class, this::loadProbeStatus)
                .orElse(new SimfinStatus(true, true, false, "error", "probe_failed"));
    }

    public Optional<SimfinCompanyRecord> fetchCompanyRecord(String rawTicker) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        String ticker = normalizeTicker(rawTicker);
        if (ticker.isBlank()) {
            return Optional.empty();
        }
        Optional<Map<String, SimfinCompanyRecord>> dataset = readCached(
                "simfin:companies",
                companyMapClass(),
                this::loadCompanyDataset
        );
        return dataset.map(map -> map.get(ticker));
    }

    public Optional<SimfinDerivedMetricsRecord> fetchDerivedMetrics(String rawTicker) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        String ticker = normalizeTicker(rawTicker);
        if (ticker.isBlank()) {
            return Optional.empty();
        }
        Optional<Map<String, SimfinDerivedMetricsRecord>> dataset = readCached(
                "simfin:derived:" + normalizeHeader(properties.getDerivedVariant()),
                derivedMapClass(),
                this::loadDerivedDataset
        );
        return dataset.map(map -> map.get(ticker));
    }

    private Optional<SimfinStatus> loadProbeStatus() {
        DatasetResponse response = downloadDataset(properties.getCompaniesDataset(), null);
        return Optional.of(mapStatus(response, "bulk-download authenticated"));
    }

    private Optional<Map<String, SimfinCompanyRecord>> loadCompanyDataset() {
        DatasetResponse response = downloadDataset(properties.getCompaniesDataset(), null);
        if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
            return Optional.empty();
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(zipInputStream, StandardCharsets.UTF_8);
                     CSVParser parser = CSVFormat.DEFAULT
                             .builder()
                             .setDelimiter(';')
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreSurroundingSpaces(true)
                             .build()
                             .parse(reader)) {
                    Map<String, SimfinCompanyRecord> results = new LinkedHashMap<>();
                    for (CSVRecord record : parser) {
                        SimfinCompanyRecord mapped = toCompanyRecord(record);
                        if (mapped != null && mapped.ticker() != null && !mapped.ticker().isBlank()) {
                            results.put(mapped.ticker(), mapped);
                        }
                    }
                    return Optional.of(results);
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Map<String, SimfinDerivedMetricsRecord>> loadDerivedDataset() {
        DatasetResponse response = downloadDataset(properties.getDerivedDataset(), properties.getDerivedVariant());
        if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
            return Optional.empty();
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(zipInputStream, StandardCharsets.UTF_8);
                     CSVParser parser = CSVFormat.DEFAULT
                             .builder()
                             .setDelimiter(';')
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreSurroundingSpaces(true)
                             .build()
                             .parse(reader)) {
                    Map<String, SimfinDerivedMetricsRecord> results = new LinkedHashMap<>();
                    for (CSVRecord record : parser) {
                        SimfinDerivedMetricsRecord mapped = toDerivedMetrics(record);
                        if (mapped == null || mapped.ticker() == null || mapped.ticker().isBlank()) {
                            continue;
                        }
                        SimfinDerivedMetricsRecord existing = results.get(mapped.ticker());
                        if (existing == null || isPreferredDerivedRecord(mapped, existing)) {
                            results.put(mapped.ticker(), mapped);
                        }
                    }
                    return Optional.of(results);
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private SimfinCompanyRecord toCompanyRecord(CSVRecord record) {
        String ticker = normalizeTicker(value(record, "Ticker", "ticker"));
        if (ticker.isBlank()) {
            return null;
        }
        return new SimfinCompanyRecord(
                ticker,
                value(record, "SimFinId", "simfinId"),
                value(record, "Company Name", "Name", "companyName"),
                value(record, "IndustryId", "industryId"),
                value(record, "Market", "market"),
                firstNonBlank(
                        value(record, "Company Description", "companyDescription"),
                        value(record, "Summary", "summary"),
                        value(record, "Business Summary", "businessSummary")
                ),
                firstNonBlank(
                        value(record, "End Fiscal Year", "endFy"),
                        value(record, "Fiscal Year End"),
                        value(record, "End of financial year (month)")
                ),
                value(record, "Number of Employees", "numEmployees", "Employees", "Number Employees"),
                value(record, "CIK", "cik"),
                value(record, "Main Currency", "currency")
        );
    }

    private SimfinDerivedMetricsRecord toDerivedMetrics(CSVRecord record) {
        String ticker = normalizeTicker(value(record, "Ticker", "ticker"));
        if (ticker.isBlank()) {
            return null;
        }
        return new SimfinDerivedMetricsRecord(
                ticker,
                value(record, "SimFinId", "simfinId"),
                parseDate(firstNonBlank(
                        value(record, "Date", "date"),
                        value(record, "Report Date", "reportDate"),
                        value(record, "Fiscal Period End", "fiscalPeriodEnd"),
                        value(record, "Publish Date", "publishDate")
                )),
                firstNonBlank(
                        value(record, "Fiscal Period", "fiscalPeriod"),
                        value(record, "Fiscal Year", "fiscalYear")
                ),
                parseNumber(firstNonBlank(
                        value(record, "Revenue Growth", "revenueGrowth"),
                        value(record, "Revenue Growth YoY", "revenueGrowthYoy"),
                        value(record, "Revenue Growth (YoY)", "revenueGrowthYoY")
                )),
                parseNumber(firstNonBlank(
                        value(record, "ROIC", "roic"),
                        value(record, "Return on Invested Capital", "returnOnInvestedCapital")
                )),
                parseNumber(firstNonBlank(
                        value(record, "Free Cash Flow Yield", "freeCashFlowYield"),
                        value(record, "FCF Yield", "fcfYield")
                ))
        );
    }

    private DatasetResponse downloadDataset(String dataset, String variant) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/bulk-download/s3")
                                .queryParam("dataset", dataset)
                                .queryParam("market", properties.getMarket());
                        if (variant != null && !variant.isBlank()) {
                            uriBuilder.queryParam("variant", variant);
                        }
                        return uriBuilder.build();
                    })
                    .header("Authorization", "api-key " + properties.getApiKey())
                    .exchange((request, response) -> {
                        byte[] body = response.getBody() == null ? new byte[0] : response.getBody().readAllBytes();
                        String bodyText = body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
                        return new DatasetResponse(response.getStatusCode().value(), body, bodyText);
                    });
        } catch (Exception ex) {
            return new DatasetResponse(599, null, ex.getMessage());
        }
    }

    private SimfinStatus mapStatus(DatasetResponse response, String successDetail) {
        if (response.statusCode() == 200) {
            return new SimfinStatus(true, true, true, "ok", successDetail);
        }
        String detail = compact(response.bodyText());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return new SimfinStatus(true, true, false, "unauthenticated", detail);
        }
        if (response.statusCode() == 408
                || response.statusCode() == 504
                || (response.statusCode() == 599 && isTimeout(detail))) {
            return new SimfinStatus(true, true, false, "timeout", detail);
        }
        return new SimfinStatus(true, true, false, "error", detail);
    }

    private boolean isPreferredDerivedRecord(
            SimfinDerivedMetricsRecord candidate,
            SimfinDerivedMetricsRecord existing
    ) {
        if (candidate.reportDate() != null && existing.reportDate() != null) {
            return candidate.reportDate().isAfter(existing.reportDate());
        }
        if (candidate.reportDate() != null) {
            return true;
        }
        if (existing.reportDate() != null) {
            return false;
        }
        return populatedDerivedMetricCount(candidate) > populatedDerivedMetricCount(existing);
    }

    private int populatedDerivedMetricCount(SimfinDerivedMetricsRecord record) {
        int count = 0;
        if (record.revenueGrowth() != null) {
            count += 1;
        }
        if (record.roic() != null) {
            count += 1;
        }
        if (record.fcfYield() != null) {
            count += 1;
        }
        return count;
    }

    private boolean isTimeout(String detail) {
        if (detail == null || detail.isBlank()) {
            return false;
        }
        String normalized = detail.toLowerCase(Locale.ROOT);
        return normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("read timed out")
                || normalized.contains("connect timed out");
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> readCached(String key, Class<T> type, SupplierWithException<Optional<T>> loader) {
        CacheEntry<Object> cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.ofNullable((T) cached.value());
        }
        try {
            Optional<T> loaded = loader.get();
            cache.put(key, new CacheEntry<>(loaded.orElse(null), now.plus(Duration.ofSeconds(properties.getCacheTtlSeconds()))));
            return loaded;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Class<Map<String, SimfinCompanyRecord>> companyMapClass() {
        return (Class<Map<String, SimfinCompanyRecord>>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private Class<Map<String, SimfinDerivedMetricsRecord>> derivedMapClass() {
        return (Class<Map<String, SimfinDerivedMetricsRecord>>) (Class<?>) Map.class;
    }

    private String value(CSVRecord record, String... keys) {
        Map<String, String> mapped = record.toMap();
        for (String key : keys) {
            for (Map.Entry<String, String> entry : mapped.entrySet()) {
                if (normalizeHeader(entry.getKey()).equals(normalizeHeader(key))) {
                    String value = entry.getValue();
                    return value == null ? null : value.trim();
                }
            }
        }
        return null;
    }

    private String normalizeTicker(String rawTicker) {
        if (rawTicker == null) {
            return "";
        }
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 200 ? singleLine : singleLine.substring(0, 200);
    }

    private Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.replace(",", "").replace("%", "").trim();
        if (trimmed.isBlank() || "NA".equalsIgnoreCase(trimmed) || "NM".equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record DatasetResponse(
            int statusCode,
            byte[] body,
            String bodyText
    ) {
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    public record SimfinStatus(
            boolean enabled,
            boolean configured,
            boolean authenticated,
            String status,
            String detail
    ) {
    }

    public record SimfinCompanyRecord(
            String ticker,
            String simfinId,
            String companyName,
            String industryId,
            String market,
            String companyDescription,
            String fiscalYearEnd,
            String numEmployees,
            String cik,
            String mainCurrency
    ) {
    }

    public record SimfinDerivedMetricsRecord(
            String ticker,
            String simfinId,
            LocalDate reportDate,
            String fiscalPeriod,
            Double revenueGrowth,
            Double roic,
            Double fcfYield
    ) {
    }
}
