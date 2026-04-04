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
            IndustryMatchPlan matchPlan = industryCandidates(sectorTemplate, securityIndustry, securitySector);
            IndustryMetricMatch beta = loadIndustryMetric(properties.getBetaUrl(), "Beta", matchPlan, false);
            IndustryMetricMatch trailingPe = loadIndustryMetric(properties.getPeUrl(), "Trailing PE", matchPlan, false);
            IndustryMetricMatch evEbitda = loadIndustryMetric(properties.getEvEbitdaUrl(), "EV/EBITDA", matchPlan, false);

            if (beta == null && trailingPe == null && evEbitda == null) {
                return Optional.empty();
            }

            String matchedIndustry = firstNonBlank(
                    beta == null ? null : beta.metric().industryName(),
                    trailingPe == null ? null : trailingPe.metric().industryName(),
                    evEbitda == null ? null : evEbitda.metric().industryName()
            );
            IndustryMetricMatch effectiveMatch = firstNonNull(beta, trailingPe, evEbitda);

            return Optional.of(new DamodaranIndustrySnapshot(
                    matchedIndustry,
                    beta == null ? null : beta.metric().value(),
                    trailingPe == null ? null : trailingPe.metric().value(),
                    evEbitda == null ? null : evEbitda.metric().value(),
                    effectiveMatch == null ? null : effectiveMatch.matchSource(),
                    effectiveMatch == null ? null : effectiveMatch.matchConfidence(),
                    effectiveMatch != null && effectiveMatch.fallbackUsed(),
                    List.of(
                            beta == null ? null : beta.metric().sourceLabel(),
                            trailingPe == null ? null : trailingPe.metric().sourceLabel(),
                            evEbitda == null ? null : evEbitda.metric().sourceLabel()
                    ).stream().filter(value -> value != null && !value.isBlank()).distinct().toList()
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private IndustryMetricMatch loadIndustryMetric(
            String url,
            String targetHeader,
            IndustryMatchPlan matchPlan,
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

        return bestIndustryMatch(metrics, matchPlan).orElse(null);
    }

    private Optional<IndustryMetricMatch> bestIndustryMatch(List<IndustryMetric> metrics, IndustryMatchPlan matchPlan) {
        if (metrics.isEmpty()) {
            return Optional.empty();
        }
        if (matchPlan == null || matchPlan.groups().isEmpty()) {
            return Optional.empty();
        }
        for (CandidateGroup group : matchPlan.groups()) {
            IndustryMetricMatch exact = findExact(metrics, group);
            if (exact != null) {
                return Optional.of(exact);
            }
        }
        for (CandidateGroup group : matchPlan.groups()) {
            IndustryMetricMatch contains = findContains(metrics, group);
            if (contains != null) {
                return Optional.of(contains);
            }
        }
        return Optional.empty();
    }

    private IndustryMetricMatch findExact(List<IndustryMetric> metrics, CandidateGroup group) {
        for (String candidate : group.aliases()) {
            String normalizedCandidate = normalizeComparable(candidate);
            for (IndustryMetric metric : metrics) {
                if (normalizeComparable(metric.industryName()).equals(normalizedCandidate)) {
                    return new IndustryMetricMatch(metric, group.matchSource(), group.matchConfidence(), group.fallbackUsed());
                }
            }
        }
        return null;
    }

    private IndustryMetricMatch findContains(List<IndustryMetric> metrics, CandidateGroup group) {
        for (String candidate : group.aliases()) {
            String normalizedCandidate = normalizeComparable(candidate);
            if (normalizedCandidate.isBlank()) {
                continue;
            }
            for (IndustryMetric metric : metrics) {
                String normalizedIndustry = normalizeComparable(metric.industryName());
                if (normalizedIndustry.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedIndustry)) {
                    return new IndustryMetricMatch(metric, group.matchSource(), group.matchConfidence(), group.fallbackUsed());
                }
            }
        }
        return null;
    }

    private IndustryMatchPlan industryCandidates(String sectorTemplate, String securityIndustry, String securitySector) {
        List<String> candidates = new ArrayList<>();
        List<CandidateGroup> groups = new ArrayList<>();
        String industry = normalizeComparable(securityIndustry);
        String sector = normalizeComparable(securitySector);
        String template = normalizeComparable(sectorTemplate);

        if (industry.contains("managed care")
                || industry.contains("health plan")
                || industry.contains("medical service plan")
                || industry.contains("hospital and medical service plan")
                || template.contains("managed care")
                || template.contains("managed_care")) {
            groups.add(new CandidateGroup(
                    "damodaran_alias_primary",
                    0.95,
                    false,
                    List.of("Healthcare Support Services")
            ));
            groups.add(new CandidateGroup(
                    "damodaran_alias_secondary",
                    0.75,
                    true,
                    List.of("Hospitals/Healthcare Facilities")
            ));
            groups.add(new CandidateGroup(
                    "damodaran_alias_fallback",
                    0.55,
                    true,
                    List.of("Insurance (General)")
            ));
        }

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
        List<String> distinctCandidates = candidates.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (!distinctCandidates.isEmpty()) {
            groups.add(new CandidateGroup(
                    "security_industry",
                    groups.isEmpty() ? 0.80 : 0.65,
                    !groups.isEmpty(),
                    distinctCandidates
            ));
        }
        return new IndustryMatchPlan(groups);
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

    private record IndustryMetricMatch(
            IndustryMetric metric,
            String matchSource,
            double matchConfidence,
            boolean fallbackUsed
    ) {
    }

    private record IndustryMatchPlan(
            List<CandidateGroup> groups
    ) {
    }

    private record CandidateGroup(
            String matchSource,
            double matchConfidence,
            boolean fallbackUsed,
            List<String> aliases
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
            String matchSource,
            Double matchConfidence,
            boolean fallbackUsed,
            List<String> sourceLabels
    ) {
        public DamodaranIndustrySnapshot(
                String matchedIndustry,
                Double beta,
                Double trailingPe,
                Double evEbitda,
                List<String> sourceLabels
        ) {
            this(
                    matchedIndustry,
                    beta,
                    trailingPe,
                    evEbitda,
                    "legacy_fixture",
                    1.0,
                    false,
                    sourceLabels
            );
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
