package com.fairvalue.engine.us;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UsDamodaranClient {
    private final UsDamodaranProperties properties;
    private final Map<String, CacheEntry<Object>> cache = new ConcurrentHashMap<>();

    public UsDamodaranClient(UsDamodaranProperties properties) {
        this.properties = properties;
    }

    public Optional<DamodaranErpSnapshot> fetchErpSnapshot() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return readCached("erp", DamodaranErpSnapshot.class, this::loadErpSnapshot);
    }

    public Optional<DamodaranIndustrySnapshot> fetchIndustrySnapshot(
            String sectorTemplate,
            String securityIndustry,
            String securitySector
    ) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String cacheKey = "industry:" + normalizeComparable(firstNonBlank(sectorTemplate, securityIndustry, securitySector, "general"));
        return readCached(cacheKey, DamodaranIndustrySnapshot.class, () -> loadIndustrySnapshot(sectorTemplate, securityIndustry, securitySector));
    }

    private Optional<DamodaranErpSnapshot> loadErpSnapshot() {
        try {
            Document document = loadDocument(properties.getErpUrl());
            List<List<String>> rows = extractRows(document);
            List<String> headers = List.of();
            int tbondIndex = -1;
            int erpIndex = -1;
            LocalDate asOf = null;

            String bodyText = document.text();
            java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("Date\\s*:\\s*([A-Za-z]+\\s+\\d{4})").matcher(bodyText);
            if (dateMatcher.find()) {
                asOf = parseMonthYear(dateMatcher.group(1));
            }

            DamodaranErpSnapshot latest = null;
            for (List<String> row : rows) {
                if (row.isEmpty()) {
                    continue;
                }
                if (headers.isEmpty() && row.stream().anyMatch(cell -> cell.contains("Implied ERP"))) {
                    headers = row;
                    tbondIndex = indexOfHeader(headers, "T.Bond Rate");
                    erpIndex = indexOfHeader(headers, "Implied ERP");
                    continue;
                }
                if (tbondIndex < 0 || erpIndex < 0 || row.size() <= Math.max(tbondIndex, erpIndex)) {
                    continue;
                }
                Double tbond = parsePercent(row.get(tbondIndex));
                Double erp = parsePercent(row.get(erpIndex));
                Integer year = parseInteger(row.get(0));
                if (tbond == null || erp == null || year == null) {
                    continue;
                }
                latest = new DamodaranErpSnapshot(
                        asOf != null ? asOf : LocalDate.of(year, 1, 1),
                        tbond,
                        erp,
                        "damodaran_histimpl"
                );
            }
            if (latest != null) {
                return Optional.of(latest);
            }
            if (properties.getFallbackErp() > 0.0) {
                return Optional.of(new DamodaranErpSnapshot(
                        LocalDate.now(),
                        Double.NaN,
                        properties.getFallbackErp(),
                        "damodaran_static_fallback"
                ));
            }
            return Optional.empty();
        } catch (Exception ignored) {
            if (properties.getFallbackErp() > 0.0) {
                return Optional.of(new DamodaranErpSnapshot(
                        LocalDate.now(),
                        Double.NaN,
                        properties.getFallbackErp(),
                        "damodaran_static_fallback"
                ));
            }
            return Optional.empty();
        }
    }

    private Optional<DamodaranIndustrySnapshot> loadIndustrySnapshot(
            String sectorTemplate,
            String securityIndustry,
            String securitySector
    ) {
        try {
            List<String> candidates = industryCandidates(sectorTemplate, securityIndustry, securitySector);
            IndustryMetric beta = loadIndustryMetric(properties.getBetaUrl(), "Beta", candidates, false);
            IndustryMetric trailingPe = loadIndustryMetric(properties.getPeUrl(), "Trailing PE", candidates, false);
            IndustryMetric evEbitda = loadIndustryMetric(properties.getEvEbitdaUrl(), "EV/EBITDA", candidates, false);

            if (beta == null && trailingPe == null && evEbitda == null) {
                return Optional.empty();
            }

            String matchedIndustry = firstNonBlank(
                    beta == null ? null : beta.industryName(),
                    trailingPe == null ? null : trailingPe.industryName(),
                    evEbitda == null ? null : evEbitda.industryName()
            );

            return Optional.of(new DamodaranIndustrySnapshot(
                    matchedIndustry,
                    beta == null ? null : beta.value(),
                    trailingPe == null ? null : trailingPe.value(),
                    evEbitda == null ? null : evEbitda.value(),
                    List.of(
                            beta == null ? null : beta.sourceLabel(),
                            trailingPe == null ? null : trailingPe.sourceLabel(),
                            evEbitda == null ? null : evEbitda.sourceLabel()
                    ).stream().filter(value -> value != null && !value.isBlank()).distinct().toList()
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private IndustryMetric loadIndustryMetric(
            String url,
            String targetHeader,
            List<String> candidates,
            boolean percent
    ) throws Exception {
        Document document = loadDocument(url);
        List<List<String>> rows = extractRows(document);
        List<String> headers = List.of();
        int industryIndex = -1;
        int targetIndex = -1;
        List<IndustryMetric> metrics = new ArrayList<>();

        for (List<String> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            boolean isHeader = row.stream().anyMatch(cell -> "Industry Name".equalsIgnoreCase(cleanCell(cell)));
            if (isHeader) {
                headers = row;
                industryIndex = indexOfHeader(headers, "Industry Name");
                targetIndex = indexOfHeader(headers, targetHeader);
                continue;
            }
            if (industryIndex < 0 || targetIndex < 0 || row.size() <= Math.max(industryIndex, targetIndex)) {
                continue;
            }
            String industry = cleanCell(row.get(industryIndex));
            if (industry.isBlank() || "Industry Name".equalsIgnoreCase(industry)) {
                continue;
            }
            Double value = percent ? parsePercent(row.get(targetIndex)) : parseNumber(row.get(targetIndex));
            if (value == null) {
                continue;
            }
            metrics.add(new IndustryMetric(industry, value, sourceLabel(targetHeader)));
        }

        return bestIndustryMatch(metrics, candidates).orElse(null);
    }

    private Optional<IndustryMetric> bestIndustryMatch(List<IndustryMetric> metrics, List<String> candidates) {
        if (metrics.isEmpty()) {
            return Optional.empty();
        }
        if (candidates == null || candidates.isEmpty()) {
            return Optional.of(metrics.get(0));
        }
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeComparable(candidate);
            for (IndustryMetric metric : metrics) {
                if (normalizeComparable(metric.industryName()).equals(normalizedCandidate)) {
                    return Optional.of(metric);
                }
            }
        }
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeComparable(candidate);
            for (IndustryMetric metric : metrics) {
                if (normalizeComparable(metric.industryName()).contains(normalizedCandidate)
                        || normalizedCandidate.contains(normalizeComparable(metric.industryName()))) {
                    return Optional.of(metric);
                }
            }
        }
        return metrics.stream()
                .min(Comparator.comparing(metric -> scoreIndustryMatch(metric.industryName(), candidates)));
    }

    private int scoreIndustryMatch(String industryName, List<String> candidates) {
        String normalizedIndustry = normalizeComparable(industryName);
        int best = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeComparable(candidate);
            if (normalizedCandidate.isBlank()) {
                continue;
            }
            if (normalizedIndustry.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedIndustry)) {
                best = Math.min(best, Math.abs(normalizedIndustry.length() - normalizedCandidate.length()));
            }
        }
        return best;
    }

    private List<String> industryCandidates(String sectorTemplate, String securityIndustry, String securitySector) {
        List<String> candidates = new ArrayList<>();
        String industry = normalizeComparable(securityIndustry);
        String sector = normalizeComparable(securitySector);
        String template = normalizeComparable(sectorTemplate);

        if (industry.contains("semiconductor")) {
            candidates.add("Semiconductor");
        }
        if (industry.contains("software")) {
            if (industry.contains("internet")) {
                candidates.add("Software (Internet)");
            }
            candidates.add("Software (System & Application)");
        }
        if (industry.contains("computer") || industry.contains("consumer electronics") || industry.contains("peripheral")) {
            candidates.add("Computers/Peripherals");
        }
        if (industry.contains("bank") || template.contains("bank")) {
            candidates.add("Bank (Money Center)");
            candidates.add("Banks (Regional)");
        }
        if (industry.contains("insurance") || template.contains("insurance")) {
            candidates.add("Insurance (General)");
        }
        if (industry.contains("reit") || template.contains("reit")) {
            candidates.add("Retail (REITs)");
        }
        if (industry.contains("telecom") || sector.contains("telecom")) {
            candidates.add("Telecom Services");
        }
        if (industry.contains("biotech") || industry.contains("pharma")) {
            candidates.add("Drugs (Biotechnology)");
            candidates.add("Pharmaceuticals");
        }
        if (industry.contains("internet")) {
            candidates.add("Software (Internet)");
        }
        if (industry.contains("financial") || sector.contains("financial")) {
            candidates.add("Financial Svcs. (Non-bank & Insurance)");
        }

        candidates.add(firstNonBlank(securityIndustry, securitySector, sectorTemplate));
        return candidates.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private Document loadDocument(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent("FairvalueEngine/0.1")
                .timeout((int) Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis())
                .get();
    }

    private List<List<String>> extractRows(Document document) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : document.select("tr")) {
            List<String> cells = tr.select("th,td").stream()
                    .map(Element::text)
                    .map(this::cleanCell)
                    .filter(cell -> !cell.isBlank())
                    .toList();
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private int indexOfHeader(List<String> headers, String headerName) {
        for (int i = 0; i < headers.size(); i += 1) {
            if (normalizeComparable(headers.get(i)).equals(normalizeComparable(headerName))) {
                return i;
            }
        }
        return -1;
    }

    private String sourceLabel(String targetHeader) {
        return switch (targetHeader) {
            case "Beta" -> "damodaran_beta";
            case "Trailing PE" -> "damodaran_pe";
            case "EV/EBITDA" -> "damodaran_ev_ebitda";
            default -> "damodaran";
        };
    }

    private String cleanCell(String cell) {
        return cell == null ? "" : cell.replace('\u00a0', ' ').trim();
    }

    private String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
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

    private Double parsePercent(String raw) {
        Double number = parseNumber(raw);
        return number == null ? null : number / 100.0;
    }

    private Integer parseInteger(String raw) {
        Double number = parseNumber(raw);
        return number == null ? null : number.intValue();
    }

    private LocalDate parseMonthYear(String raw) {
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);
            return java.time.YearMonth.parse(raw.trim(), formatter).atDay(1);
        } catch (Exception ex) {
            return null;
        }
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    private record IndustryMetric(
            String industryName,
            double value,
            String sourceLabel
    ) {
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    public record DamodaranErpSnapshot(
            LocalDate asOf,
            double treasuryRate,
            double impliedErp,
            String sourceLabel
    ) {
    }

    public record DamodaranIndustrySnapshot(
            String matchedIndustry,
            Double beta,
            Double trailingPe,
            Double evEbitda,
            List<String> sourceLabels
    ) {
    }
}
