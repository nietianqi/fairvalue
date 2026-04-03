package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.SecurityMasterRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UsSecurityClassificationService {
    private static final String RULES_PATH = "rules/us-security-classification-rules.json";

    private final SecurityMasterRepository securityMasterRepository;
    private final UsSecurityMasterService usSecurityMasterService;
    private final ObjectMapper objectMapper;

    private volatile ClassificationRules cachedRules;

    public UsSecurityClassificationService(
            SecurityMasterRepository securityMasterRepository,
            UsSecurityMasterService usSecurityMasterService,
            ObjectMapper objectMapper
    ) {
        this.securityMasterRepository = securityMasterRepository;
        this.usSecurityMasterService = usSecurityMasterService;
        this.objectMapper = objectMapper;
    }

    public UsSecurityMaster ensureClassification(
            String rawTicker,
            StockSnapshot snapshot,
            UsSecClient.UsSecProfile profile
    ) {
        ClassificationRules rules = rules();
        UsSecurityMaster current = usSecurityMasterService.findByTicker(rawTicker)
                .orElseGet(() -> new UsSecurityMaster(
                        null,
                        normalizeTicker(rawTicker),
                        normalizeTicker(rawTicker) + ".US",
                        firstNonBlank(profile == null ? null : profile.companyName(), snapshot == null ? null : snapshot.companyName(), normalizeTicker(rawTicker)),
                        firstNonBlank(profile == null ? null : profile.exchange(), inferExchange(rawTicker)),
                        "USD",
                        null,
                        snapshot == null ? null : snapshot.industry(),
                        null,
                        null,
                        null,
                        "US",
                        true
                ));

        SecurityContext context = buildContext(current, snapshot, profile, rules);
        ClassificationRule matchedRule = rules.safeRules().stream()
                .sorted(Comparator.comparingInt(ClassificationRule::safePriority).reversed())
                .filter(rule -> matches(rule, context))
                .findFirst()
                .orElse(new ClassificationRule("default", 0, rules.defaultCompanyType(), rules.defaultSectorTemplate(),
                        List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null));

        String exchange = firstNonBlank(profile == null ? null : profile.exchange(), current.exchange(), inferExchange(current.ticker()));
        String companyName = firstNonBlank(profile == null ? null : profile.companyName(), current.companyName(), snapshot == null ? null : snapshot.companyName());
        String industry = firstNonBlank(profile == null ? null : profile.industry(), current.industry(), snapshot == null ? null : snapshot.industry());

        UsSecurityMaster updated = new UsSecurityMaster(
                current.id(),
                current.ticker(),
                current.symbolFull(),
                companyName,
                exchange,
                current.currency(),
                context.sector(),
                industry,
                current.subindustry(),
                matchedRule.companyType(),
                matchedRule.sectorTemplate(),
                current.country(),
                current.active()
        );
        if (current.id() != null && !sameClassification(current, updated)) {
            securityMasterRepository.update(current.id(), updated);
        }
        return updated;
    }

    private SecurityContext buildContext(
            UsSecurityMaster current,
            StockSnapshot snapshot,
            UsSecClient.UsSecProfile profile,
            ClassificationRules rules
    ) {
        String industry = firstNonBlank(profile == null ? null : profile.industry(), current.industry(), snapshot == null ? null : snapshot.industry());
        String sector = inferSector(industry, rules);
        double revenueGrowth = snapshot == null ? 0.0 : snapshot.fundamentals().revenueGrowth();
        double fcfMargin = snapshot == null ? 0.0 : snapshot.fundamentals().fcfMargin();
        double dividendYield = snapshot == null ? 0.0 : snapshot.fundamentals().dividendYield();
        double pe = snapshot == null ? 0.0 : snapshot.fundamentals().pe();
        double roe = snapshot == null ? 0.0 : snapshot.fundamentals().roe();
        double earningsVolatility = snapshot == null ? 0.0 : snapshot.fundamentals().earningsVolatility();
        return new SecurityContext(sector, industry, revenueGrowth, fcfMargin, dividendYield, pe, roe, earningsVolatility);
    }

    private boolean matches(ClassificationRule rule, SecurityContext context) {
        if (!matchesKeywords(rule.safeSectorKeywords(), context.sector())) {
            return false;
        }
        if (!matchesKeywords(rule.safeIndustryKeywords(), context.industry())) {
            return false;
        }
        List<String> excludeKeywords = rule.safeKeywordsExclude();
        if (!excludeKeywords.isEmpty()
                && (matchesKeywords(excludeKeywords, context.sector())
                    || matchesKeywords(excludeKeywords, context.industry()))) {
            return false;
        }
        if (!greaterOrEqual(context.revenueGrowth(), rule.revenueGrowthMin())) {
            return false;
        }
        if (!lessOrEqual(context.revenueGrowth(), rule.revenueGrowthMax())) {
            return false;
        }
        if (!greaterOrEqual(context.fcfMargin(), rule.fcfMarginMin())) {
            return false;
        }
        if (!lessOrEqual(context.fcfMargin(), rule.fcfMarginMax())) {
            return false;
        }
        if (!greaterOrEqual(context.dividendYield(), rule.dividendYieldMin())) {
            return false;
        }
        if (!greaterOrEqual(context.roe(), rule.roeMin())) {
            return false;
        }
        if (!lessOrEqual(context.pe(), rule.peMax())) {
            return false;
        }
        if (!greaterOrEqual(context.earningsVolatility(), rule.earningsVolatilityMin())) {
            return false;
        }
        return true;
    }

    private boolean matchesKeywords(List<String> keywords, String haystack) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        String normalized = haystack.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean greaterOrEqual(double value, Double threshold) {
        return threshold == null || value >= threshold;
    }

    private boolean lessOrEqual(double value, Double threshold) {
        return threshold == null || value <= threshold;
    }

    private String inferSector(String industry, ClassificationRules rules) {
        if (industry == null || industry.isBlank()) {
            return rules.defaultSector();
        }
        String normalized = industry.toLowerCase(Locale.ROOT);
        return rules.safeSectorAliases().stream()
                .filter(alias -> alias.safeKeywords().stream().anyMatch(normalized::contains))
                .map(SectorAlias::sector)
                .findFirst()
                .orElse(rules.defaultSector());
    }

    private String inferExchange(String ticker) {
        return ticker != null && ticker.startsWith("N") ? "NYSE" : "NASDAQ";
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker == null
                ? ""
                : rawTicker.trim().toUpperCase(Locale.ROOT).replace(".US", "").replace(".", "-");
    }

    private boolean sameClassification(UsSecurityMaster left, UsSecurityMaster right) {
        return java.util.Objects.equals(left.companyName(), right.companyName())
                && java.util.Objects.equals(left.exchange(), right.exchange())
                && java.util.Objects.equals(left.sector(), right.sector())
                && java.util.Objects.equals(left.industry(), right.industry())
                && java.util.Objects.equals(left.companyType(), right.companyType())
                && java.util.Objects.equals(left.sectorTemplate(), right.sectorTemplate());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private ClassificationRules rules() {
        ClassificationRules local = cachedRules;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedRules == null) {
                cachedRules = loadRules();
            }
            return cachedRules;
        }
    }

    private ClassificationRules loadRules() {
        try (InputStream inputStream = new ClassPathResource(RULES_PATH).getInputStream()) {
            return objectMapper.readValue(inputStream, ClassificationRules.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load US security classification rules.", ex);
        }
    }

    private record SecurityContext(
            String sector,
            String industry,
            double revenueGrowth,
            double fcfMargin,
            double dividendYield,
            double pe,
            double roe,
            double earningsVolatility
    ) {
    }

    public record ClassificationRules(
            String defaultSector,
            String defaultCompanyType,
            String defaultSectorTemplate,
            List<SectorAlias> sectorAliases,
            List<ClassificationRule> rules
    ) {
        public String defaultSector() {
            return defaultSector == null || defaultSector.isBlank() ? "General" : defaultSector;
        }

        public String defaultCompanyType() {
            return defaultCompanyType == null || defaultCompanyType.isBlank() ? "general_quality" : defaultCompanyType;
        }

        public String defaultSectorTemplate() {
            return defaultSectorTemplate == null || defaultSectorTemplate.isBlank() ? "us_general_quality" : defaultSectorTemplate;
        }

        public List<SectorAlias> safeSectorAliases() {
            return sectorAliases == null ? List.of() : sectorAliases;
        }

        public List<ClassificationRule> safeRules() {
            return rules == null ? List.of() : rules;
        }
    }

    public record SectorAlias(
            String sector,
            List<String> keywords
    ) {
        public List<String> safeKeywords() {
            return keywords == null ? List.of() : keywords;
        }
    }

    public record ClassificationRule(
            String id,
            Integer priority,
            String companyType,
            String sectorTemplate,
            List<String> sectorKeywords,
            List<String> industryKeywords,
            List<String> keywordsExclude,
            Double revenueGrowthMin,
            Double revenueGrowthMax,
            Double fcfMarginMin,
            Double fcfMarginMax,
            Double dividendYieldMin,
            Double peMax,
            Double roeMin,
            Double earningsVolatilityMin
    ) {
        public int safePriority() {
            return priority == null ? 0 : priority;
        }

        public List<String> safeSectorKeywords() {
            return sectorKeywords == null ? List.of() : sectorKeywords;
        }

        public List<String> safeIndustryKeywords() {
            return industryKeywords == null ? List.of() : industryKeywords;
        }

        /** Keywords matched against both sector AND industry — if any match, the rule is excluded. */
        public List<String> safeKeywordsExclude() {
            return keywordsExclude == null ? List.of() : keywordsExclude;
        }
    }
}
