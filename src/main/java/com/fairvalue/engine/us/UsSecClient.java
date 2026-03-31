package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UsSecClient {
    private static final List<String> REVENUE_CONCEPTS = List.of(
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet"
    );
    private static final List<String> NET_INCOME_CONCEPTS = List.of(
            "NetIncomeLoss",
            "ProfitLoss"
    );
    private static final List<String> OCF_CONCEPTS = List.of(
            "NetCashProvidedByUsedInOperatingActivities",
            "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"
    );
    private static final List<String> CAPEX_CONCEPTS = List.of(
            "PaymentsToAcquirePropertyPlantAndEquipment",
            "CapitalExpendituresIncurredButNotYetPaid",
            "PropertyPlantAndEquipmentAdditions"
    );
    private static final List<String> CASH_CONCEPTS = List.of(
            "CashAndCashEquivalentsAtCarryingValue",
            "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"
    );
    private static final List<String> DEBT_CONCEPTS = List.of(
            "LongTermDebtAndCapitalLeaseObligations",
            "LongTermDebtAndFinanceLeaseObligations",
            "LongTermDebtNoncurrent",
            "LongTermDebt"
    );
    private static final List<String> CURRENT_DEBT_CONCEPTS = List.of(
            "LongTermDebtAndCapitalLeaseObligationsCurrent",
            "LongTermDebtCurrent",
            "ShortTermBorrowings"
    );
    private static final List<String> EQUITY_CONCEPTS = List.of(
            "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
            "StockholdersEquity"
    );
    private static final List<String> EBITDA_CONCEPTS = List.of(
            "OperatingIncomeLoss",
            "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest"
    );
    private static final List<String> SBC_CONCEPTS = List.of(
            "ShareBasedCompensation",
            "AllocatedShareBasedCompensationExpense"
    );

    private final RestClient secRestClient;
    private final RestClient genericRestClient;
    private final ObjectMapper objectMapper;
    private final UsLiveMarketDataProperties properties;
    private final Map<String, CacheEntry<Optional<UsSecProfile>>> profileCache = new ConcurrentHashMap<>();
    private volatile CacheEntry<Map<String, SecTickerInfo>> tickerUniverseCache;

    public UsSecClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            UsLiveMarketDataProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.secRestClient = restClientBuilder
                .baseUrl(properties.getSecBaseUrl())
                .defaultHeader("User-Agent", properties.getSecUserAgent())
                .build();
        this.genericRestClient = restClientBuilder
                .defaultHeader("User-Agent", properties.getSecUserAgent())
                .build();
    }

    public Optional<UsSecProfile> fetchProfile(String ticker) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String normalized = normalizeTicker(ticker);
        CacheEntry<Optional<UsSecProfile>> cached = profileCache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        Optional<UsSecProfile> loaded = loadProfile(normalized);
        profileCache.put(normalized, new CacheEntry<>(loaded, now.plus(Duration.ofSeconds(properties.getProfileCacheTtlSeconds()))));
        return loaded;
    }

    public Optional<SecTickerInfo> fetchTickerInfo(String ticker) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadTickerUniverse(false).get(normalizeTicker(ticker)));
    }

    public List<SecTickerInfo> fetchTickerUniverse() {
        return fetchTickerUniverse(false);
    }

    public List<SecTickerInfo> fetchTickerUniverse(boolean forceRefresh) {
        return loadTickerUniverse(forceRefresh).values().stream()
                .sorted(Comparator.comparing(SecTickerInfo::ticker))
                .toList();
    }

    public Optional<JsonNode> fetchSubmissions(String ticker) {
        return fetchTickerInfo(ticker).flatMap(info -> fetchSubmissionsByCik(info.cik()));
    }

    public Optional<JsonNode> fetchCompanyFacts(String ticker) {
        return fetchTickerInfo(ticker).flatMap(info -> fetchCompanyFactsByCik(info.cik()));
    }

    private Optional<UsSecProfile> loadProfile(String ticker) {
        Optional<SecTickerInfo> tickerInfo = findTickerInfo(ticker);
        if (tickerInfo.isEmpty()) {
            return Optional.empty();
        }

        try {
            SecTickerInfo info = tickerInfo.get();
            JsonNode submissions = fetchSubmissionsByCik(info.cik()).orElse(null);
            JsonNode companyFacts = fetchCompanyFactsByCik(info.cik()).orElse(null);
            if (submissions == null || companyFacts == null) {
                return Optional.empty();
            }

            FilingSnapshot filingSnapshot = parseFilingSnapshot(submissions);
            FundamentalsSnapshot fundamentals = parseFundamentals(companyFacts);

            return Optional.of(new UsSecProfile(
                    ticker,
                    submissions.path("name").asText(info.title()),
                    firstTextArray(submissions.path("exchanges"), "NASDAQ"),
                    firstTextArray(submissions.path("tickers"), ticker),
                    textOrNull(submissions.path("sicDescription")),
                    filingSnapshot.latest10kDate(),
                    filingSnapshot.latest10qDate(),
                    filingSnapshot.hasRecent8k(),
                    fundamentals.sharesOutstanding(),
                    fundamentals.annualRevenue(),
                    fundamentals.previousAnnualRevenue(),
                    fundamentals.annualNetIncome(),
                    fundamentals.annualOperatingCashFlow(),
                    fundamentals.annualCapex(),
                    fundamentals.cash(),
                    fundamentals.totalDebt(),
                    fundamentals.equity(),
                    fundamentals.ebitdaProxy(),
                    fundamentals.stockBasedCompensation(),
                    fundamentals.dataCompleteness()
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<SecTickerInfo> findTickerInfo(String ticker) {
        return fetchTickerInfo(ticker);
    }

    private Optional<JsonNode> fetchSubmissionsByCik(long cik) {
        return fetchSecJson("/submissions/CIK" + paddedCik(cik) + ".json");
    }

    private Optional<JsonNode> fetchCompanyFactsByCik(long cik) {
        return fetchSecJson("/api/xbrl/companyfacts/CIK" + paddedCik(cik) + ".json");
    }

    private Optional<JsonNode> fetchSecJson(String uri) {
        try {
            return Optional.of(objectMapper.readTree(secRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Map<String, SecTickerInfo> loadTickerUniverse(boolean forceRefresh) {
        CacheEntry<Map<String, SecTickerInfo>> cached = tickerUniverseCache;
        Instant now = Instant.now();
        if (!forceRefresh && cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        try {
            JsonNode root = objectMapper.readTree(genericRestClient.get()
                    .uri(properties.getSecTickersUrl())
                    .retrieve()
                    .body(String.class));

            Map<String, SecTickerInfo> loaded = new LinkedHashMap<>();
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                String ticker = normalizeTicker(node.path("ticker").asText(""));
                if (ticker.isBlank()) {
                    continue;
                }
                loaded.put(ticker, new SecTickerInfo(
                        ticker,
                        node.path("cik_str").asLong(),
                        node.path("title").asText()
                ));
            }
            tickerUniverseCache = new CacheEntry<>(
                    loaded,
                    now.plus(Duration.ofSeconds(properties.getTickerUniverseCacheTtlSeconds()))
            );
            return loaded;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load SEC ticker universe.", ex);
        }
    }

    private FilingSnapshot parseFilingSnapshot(JsonNode submissions) {
        JsonNode recent = submissions.path("filings").path("recent");
        int size = recent.path("form").size();
        LocalDate latest10k = null;
        LocalDate latest10q = null;
        boolean hasRecent8k = false;

        for (int i = 0; i < size; i += 1) {
            String form = recent.path("form").path(i).asText("");
            LocalDate filed = parseDate(recent.path("filingDate").path(i).asText(null));
            if (filed == null) {
                continue;
            }

            if (latest10k == null && ("10-K".equals(form) || "20-F".equals(form))) {
                latest10k = filed;
            }
            if (latest10q == null && ("10-Q".equals(form) || "6-K".equals(form))) {
                latest10q = filed;
            }
            if (!hasRecent8k && ("8-K".equals(form) || "8-K/A".equals(form)) && filed.isAfter(LocalDate.now().minusDays(90))) {
                hasRecent8k = true;
            }
        }

        return new FilingSnapshot(latest10k, latest10q, hasRecent8k);
    }

    private FundamentalsSnapshot parseFundamentals(JsonNode companyFacts) {
        JsonNode dei = companyFacts.path("facts").path("dei");
        JsonNode gaap = companyFacts.path("facts").path("us-gaap");

        Double sharesOutstanding = latestInstant(dei, "EntityCommonStockSharesOutstanding", "shares");
        Double annualRevenue = latestAnnualFlow(gaap, REVENUE_CONCEPTS, "USD");
        Double previousAnnualRevenue = previousAnnualFlow(gaap, REVENUE_CONCEPTS, "USD");
        Double annualNetIncome = latestAnnualFlow(gaap, NET_INCOME_CONCEPTS, "USD");
        Double annualOperatingCashFlow = latestAnnualFlow(gaap, OCF_CONCEPTS, "USD");
        Double annualCapex = latestAnnualFlow(gaap, CAPEX_CONCEPTS, "USD");
        Double cash = latestInstant(gaap, CASH_CONCEPTS, "USD");
        Double totalDebt = sum(
                latestInstant(gaap, DEBT_CONCEPTS, "USD"),
                latestInstant(gaap, CURRENT_DEBT_CONCEPTS, "USD")
        );
        Double equity = latestInstant(gaap, EQUITY_CONCEPTS, "USD");
        Double ebitdaProxy = latestAnnualFlow(gaap, EBITDA_CONCEPTS, "USD");
        Double sbc = latestAnnualFlow(gaap, SBC_CONCEPTS, "USD");

        double completeness = 0.30;
        completeness += sharesOutstanding != null ? 0.10 : 0.0;
        completeness += annualRevenue != null ? 0.12 : 0.0;
        completeness += annualNetIncome != null ? 0.12 : 0.0;
        completeness += annualOperatingCashFlow != null ? 0.10 : 0.0;
        completeness += cash != null ? 0.08 : 0.0;
        completeness += totalDebt != null ? 0.08 : 0.0;
        completeness += equity != null ? 0.08 : 0.0;
        completeness += ebitdaProxy != null ? 0.06 : 0.0;
        completeness += sbc != null ? 0.06 : 0.0;

        return new FundamentalsSnapshot(
                sharesOutstanding,
                annualRevenue,
                previousAnnualRevenue,
                annualNetIncome,
                annualOperatingCashFlow,
                annualCapex,
                cash,
                totalDebt,
                equity,
                ebitdaProxy,
                sbc,
                Math.min(completeness, 0.96)
        );
    }

    private Double latestInstant(JsonNode parent, String concept, String unit) {
        return latestInstant(parent, List.of(concept), unit);
    }

    private Double latestInstant(JsonNode parent, List<String> concepts, String unit) {
        List<ValuePoint> values = new ArrayList<>();
        for (String concept : concepts) {
            JsonNode items = parent.path(concept).path("units").path(unit);
            collectValues(items, values, false);
        }
        return values.stream()
                .max(Comparator.comparing(ValuePoint::end).thenComparing(ValuePoint::filed))
                .map(ValuePoint::value)
                .orElse(null);
    }

    private Double latestAnnualFlow(JsonNode parent, List<String> concepts, String unit) {
        List<ValuePoint> values = annualValues(parent, concepts, unit);
        return values.isEmpty() ? null : values.get(values.size() - 1).value();
    }

    private Double previousAnnualFlow(JsonNode parent, List<String> concepts, String unit) {
        List<ValuePoint> values = annualValues(parent, concepts, unit);
        return values.size() < 2 ? null : values.get(values.size() - 2).value();
    }

    private List<ValuePoint> annualValues(JsonNode parent, List<String> concepts, String unit) {
        List<ValuePoint> values = new ArrayList<>();
        for (String concept : concepts) {
            JsonNode items = parent.path(concept).path("units").path(unit);
            collectValues(items, values, true);
        }
        values.sort(Comparator.comparing(ValuePoint::end).thenComparing(ValuePoint::filed));
        List<ValuePoint> deduped = new ArrayList<>();
        for (ValuePoint value : values) {
            if (deduped.stream().noneMatch(existing -> existing.end().equals(value.end()))) {
                deduped.add(value);
            }
        }
        return deduped;
    }

    private void collectValues(JsonNode items, List<ValuePoint> target, boolean annualOnly) {
        if (!items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            LocalDate end = parseDate(item.path("end").asText(null));
            LocalDate filed = parseDate(item.path("filed").asText(null));
            if (end == null || filed == null || !item.has("val")) {
                continue;
            }

            if (annualOnly) {
                String form = item.path("form").asText("");
                LocalDate start = parseDate(item.path("start").asText(null));
                if (start == null || !(form.equals("10-K") || form.equals("20-F") || form.equals("10-K/A"))) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(start, end);
                if (days < 300) {
                    continue;
                }
            }

            target.add(new ValuePoint(end, filed, item.path("val").asDouble()));
        }
    }

    private String paddedCik(long cik) {
        return String.format("%010d", cik);
    }

    private String firstTextArray(JsonNode node, String fallback) {
        return node.isArray() && node.size() > 0 ? node.path(0).asText(fallback) : fallback;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw);
    }

    private double sum(Double left, Double right) {
        double a = left == null ? 0.0 : left;
        double b = right == null ? 0.0 : right;
        double total = a + b;
        return total > 0 ? total : 0.0;
    }

    private String normalizeTicker(String ticker) {
        return ticker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".", "-");
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    public record SecTickerInfo(String ticker, long cik, String title) {
    }

    private record FilingSnapshot(
            LocalDate latest10kDate,
            LocalDate latest10qDate,
            boolean hasRecent8k
    ) {
    }

    private record FundamentalsSnapshot(
            Double sharesOutstanding,
            Double annualRevenue,
            Double previousAnnualRevenue,
            Double annualNetIncome,
            Double annualOperatingCashFlow,
            Double annualCapex,
            Double cash,
            Double totalDebt,
            Double equity,
            Double ebitdaProxy,
            Double stockBasedCompensation,
            double dataCompleteness
    ) {
    }

    private record ValuePoint(LocalDate end, LocalDate filed, double value) {
    }

    public record UsSecProfile(
            String ticker,
            String companyName,
            String exchange,
            String primaryTicker,
            String industry,
            LocalDate latest10kDate,
            LocalDate latest10qDate,
            boolean hasRecent8k,
            Double sharesOutstanding,
            Double annualRevenue,
            Double previousAnnualRevenue,
            Double annualNetIncome,
            Double annualOperatingCashFlow,
            Double annualCapex,
            Double cash,
            Double totalDebt,
            Double equity,
            Double ebitdaProxy,
            Double stockBasedCompensation,
            double dataCompleteness
    ) {
    }
}
