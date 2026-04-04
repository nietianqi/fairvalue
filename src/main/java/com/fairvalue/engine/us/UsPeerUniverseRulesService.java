package com.fairvalue.engine.us;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UsPeerUniverseRulesService {
    private static final String RULE_VERSION = "v3_industry_specific";

    private final Map<String, UsPeerUniverseRuleProfile> rules;

    public UsPeerUniverseRulesService() {
        this.rules = buildRules();
    }

    public UsPeerUniverseRuleProfile resolve(UsSecurityMaster security) {
        if (security == null) {
            return rules.get("default");
        }
        String companyType = normalize(security.companyType());
        String sectorTemplate = normalize(security.sectorTemplate());
        if (companyType != null && rules.containsKey(companyType)) {
            return rules.get(companyType);
        }
        if (sectorTemplate != null && rules.containsKey(sectorTemplate)) {
            return rules.get(sectorTemplate);
        }
        return rules.get("default");
    }

    private Map<String, UsPeerUniverseRuleProfile> buildRules() {
        Map<String, UsPeerUniverseRuleProfile> values = new LinkedHashMap<>();
        values.put("default", new UsPeerUniverseRuleProfile(
                "default",
                RULE_VERSION,
                List.of("industry", "sector_template", "company_type", "sector"),
                List.of("market_cap", "revenue_growth", "fcf_margin", "usable_multiple"),
                5,
                3,
                -1.0,
                -1.0,
                0.20,
                5.0,
                0.18,
                0.18,
                null,
                18.0,
                null,
                0.12,
                7.0,
                null,
                null,
                null,
                null,
                null
        ));
        values.put("compounder", new UsPeerUniverseRuleProfile(
                "compounder",
                RULE_VERSION,
                List.of("industry", "sector_template", "company_type", "sector"),
                List.of("market_cap", "revenue_growth", "fcf_margin", "roic", "usable_multiple"),
                6,
                3,
                0.08,
                0.08,
                0.25,
                4.0,
                0.12,
                0.18,
                0.12,
                16.0,
                null,
                0.15,
                5.5,
                0.18,
                0.15,
                0.20,
                null,
                null
        ));
        values.put("hypergrowth_saas", new UsPeerUniverseRuleProfile(
                "hypergrowth_saas",
                RULE_VERSION,
                List.of("sector_template", "industry", "company_type", "sector"),
                List.of("market_cap", "revenue_growth", "fcf_margin", "usable_multiple"),
                7,
                2,
                -0.10,
                -1.0,
                0.15,
                6.0,
                0.20,
                0.18,
                null,
                20.0,
                null,
                0.10,
                8.0,
                0.28,
                0.24,
                null,
                24.0,
                null
        ));
        values.put("financial_quality", new UsPeerUniverseRuleProfile(
                "financial_quality",
                RULE_VERSION,
                List.of("industry", "company_type", "sector_template", "sector"),
                List.of("market_cap", "pb", "pe", "usable_multiple"),
                6,
                3,
                -1.0,
                -1.0,
                0.20,
                6.0,
                null,
                null,
                null,
                10.0,
                1.8,
                0.12,
                8.0,
                null,
                null,
                null,
                14.0,
                2.5
        ));
        values.put("managed_care", new UsPeerUniverseRuleProfile(
                "managed_care",
                RULE_VERSION,
                List.of("industry", "sector_template", "company_type", "sector"),
                List.of("market_cap", "revenue_growth", "fcf_margin", "pe", "usable_multiple"),
                8,
                3,
                0.02,
                -1.0,
                0.25,
                5.5,
                0.10,
                0.10,
                null,
                8.0,
                null,
                0.18,
                8.0,
                0.14,
                0.14,
                null,
                10.0,
                null
        ));
        values.put("bank", new UsPeerUniverseRuleProfile(
                "bank",
                RULE_VERSION,
                List.of("industry", "company_type", "sector"),
                List.of("market_cap", "pb", "pe", "usable_multiple"),
                7,
                3,
                -1.0,
                -1.0,
                0.18,
                7.0,
                null,
                null,
                null,
                8.0,
                1.4,
                0.10,
                9.0,
                null,
                null,
                null,
                10.0,
                2.0
        ));
        values.put("reit", new UsPeerUniverseRuleProfile(
                "reit",
                RULE_VERSION,
                List.of("industry", "sector_template", "sector"),
                List.of("market_cap", "pb", "usable_multiple"),
                6,
                3,
                -1.0,
                -1.0,
                0.20,
                6.0,
                null,
                null,
                null,
                null,
                1.6,
                0.12,
                8.0,
                null,
                null,
                null,
                null,
                2.1
        ));
        values.put("biotech", new UsPeerUniverseRuleProfile(
                "biotech",
                RULE_VERSION,
                List.of("industry", "sector_template", "sector"),
                List.of("market_cap", "revenue_growth", "usable_multiple"),
                7,
                2,
                -1.0,
                -1.0,
                0.10,
                8.0,
                0.30,
                null,
                null,
                24.0,
                null,
                0.08,
                10.0,
                0.40,
                null,
                null,
                30.0,
                null
        ));
        values.put("income_defensive", new UsPeerUniverseRuleProfile(
                "income_defensive",
                RULE_VERSION,
                List.of("industry", "sector", "company_type"),
                List.of("market_cap", "fcf_margin", "pe", "usable_multiple"),
                5,
                3,
                0.02,
                -1.0,
                0.25,
                5.0,
                null,
                0.14,
                null,
                10.0,
                null,
                0.12,
                7.0,
                null,
                0.20,
                null,
                14.0,
                null
        ));
        values.put("cyclical", new UsPeerUniverseRuleProfile(
                "cyclical",
                RULE_VERSION,
                List.of("industry", "sector", "company_type"),
                List.of("market_cap", "revenue_growth", "usable_multiple"),
                5,
                3,
                -1.0,
                -1.0,
                0.15,
                6.0,
                0.22,
                null,
                null,
                14.0,
                null,
                0.10,
                8.0,
                0.30,
                null,
                null,
                18.0,
                null
        ));
        values.put("us_tech_compounder", values.get("compounder"));
        values.put("us_hypergrowth_saas", values.get("hypergrowth_saas"));
        values.put("us_financial_quality", values.get("financial_quality"));
        values.put("us_bank", values.get("bank"));
        values.put("us_reit", values.get("reit"));
        values.put("us_biotech", values.get("biotech"));
        values.put("us_cyclical", values.get("cyclical"));
        values.put("us_managed_care", values.get("managed_care"));
        values.put("us_general_quality", values.get("default"));
        return values;
    }

    private String normalize(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
