package com.fairvalue.engine.cn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CnRulesProvider {
    private final JsonNode root;

    public CnRulesProvider(ObjectMapper objectMapper) {
        try (InputStream stream = new ClassPathResource("rules/cn_stock_valuation_rules_v1.json").getInputStream()) {
            this.root = objectMapper.readTree(stream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load CN valuation rules.", ex);
        }
    }

    public String name() {
        return root.path("name").asText("cn_stock_valuation_engine");
    }

    public String version() {
        return root.path("version").asText("1.0.0");
    }

    public int minimumModelCount() {
        return root.path("model_selection_rules").path("minimum_model_count").asInt(3);
    }

    public String masterFormula() {
        return root.path("valuation_master_formula").path("formula").asText("FV = SUM(method_value_i * method_weight_i) * china_adjustment_factor");
    }

    public List<String> requiredScenarios() {
        return textList(root.path("scenario_engine").path("required_scenarios"));
    }

    public Map<String, Double> defaultScenarioWeights() {
        JsonNode node = root.path("scenario_engine").path("default_probability_weights");
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("bear", node.path("bear").asDouble(0.25));
        result.put("base", node.path("base").asDouble(0.55));
        result.put("bull", node.path("bull").asDouble(0.20));
        return result;
    }

    public List<String> requiredSections() {
        return textList(root.path("output_schema").path("required_sections"));
    }

    public List<String> methodsForCompanyType(String companyType) {
        for (JsonNode node : root.path("company_type_framework")) {
            if (companyType.equalsIgnoreCase(node.path("type").asText(""))) {
                List<String> methods = new ArrayList<>();
                methods.addAll(textList(node.path("primary_models")));
                methods.addAll(textList(node.path("secondary_models")));
                return methods;
            }
        }
        return List.of("PE", "DCF", "EV_EBITDA");
    }

    public List<String> methodsForIndustry(String industryRoute) {
        for (JsonNode node : root.path("industry_router")) {
            if (industryRoute.equalsIgnoreCase(node.path("industry").asText(""))) {
                List<String> methods = new ArrayList<>();
                methods.addAll(textList(node.path("primary_models")));
                methods.addAll(textList(node.path("secondary_models")));
                return methods;
            }
        }
        return List.of();
    }

    public Map<String, Double> weightingForCase(String caseName) {
        for (JsonNode node : root.path("weighting_rules").path("examples")) {
            if (caseName.equalsIgnoreCase(node.path("case").asText(""))) {
                Map<String, Double> weights = new LinkedHashMap<>();
                JsonNode weightNode = node.path("weights");
                weightNode.fieldNames().forEachRemaining(field -> weights.put(normalize(field), weightNode.path(field).asDouble()));
                return weights;
            }
        }
        return Map.of();
    }

    public String normalize(String method) {
        return method.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('/', '_');
    }

    private List<String> textList(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            values.add(item.asText());
        }
        return values;
    }
}
