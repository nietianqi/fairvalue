package com.fairvalue.engine.us;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.SectorTemplateConfigRepository;
import com.fairvalue.engine.repository.ValuationParameterSetRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UsValuationConfigService {
    private static final String DEFAULT_TEMPLATE = "us_general_quality";
    private static final List<String> DEFAULT_METHODS = List.of(
            "dcf",
            "historical_multiple",
            "relative_valuation",
            "reverse_dcf"
    );
    private static final Set<String> SUPPORTED_METHODS = Set.copyOf(DEFAULT_METHODS);

    private final SectorTemplateConfigRepository sectorTemplateConfigRepository;
    private final ValuationParameterSetRepository valuationParameterSetRepository;
    private final ObjectMapper objectMapper;

    public UsValuationConfigService(
            SectorTemplateConfigRepository sectorTemplateConfigRepository,
            ValuationParameterSetRepository valuationParameterSetRepository,
            ObjectMapper objectMapper
    ) {
        this.sectorTemplateConfigRepository = sectorTemplateConfigRepository;
        this.valuationParameterSetRepository = valuationParameterSetRepository;
        this.objectMapper = objectMapper;
    }

    public UsResolvedValuationConfig resolve(Long securityId, String sectorTemplate, LocalDate effectiveDate) {
        String resolvedTemplate = normalizeTemplate(sectorTemplate);
        UsSectorTemplateConfigRecord templateRecord = sectorTemplateConfigRepository.findBySectorTemplate(resolvedTemplate)
                .or(() -> sectorTemplateConfigRepository.findBySectorTemplate(DEFAULT_TEMPLATE))
                .orElse(null);

        List<String> primaryMethods = resolvePrimaryMethods(templateRecord);
        Map<String, Double> defaultWeights = resolveDefaultWeights(templateRecord, primaryMethods);
        Map<String, Double> parameterMap = resolveParameterMap(securityId, resolvedTemplate, effectiveDate);
        double marginOfSafety = resolveMarginOfSafety(templateRecord);
        List<String> riskNotes = parseStringList(templateRecord == null ? null : templateRecord.riskNotesJson());

        return new UsResolvedValuationConfig(
                resolvedTemplate,
                primaryMethods,
                normalizeWeights(defaultWeights, primaryMethods),
                parameterMap,
                marginOfSafety,
                riskNotes
        );
    }

    private String normalizeTemplate(String sectorTemplate) {
        if (sectorTemplate == null || sectorTemplate.isBlank()) {
            return DEFAULT_TEMPLATE;
        }
        return sectorTemplate.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> resolvePrimaryMethods(UsSectorTemplateConfigRecord record) {
        List<String> configured = parseStringList(record == null ? null : record.primaryMethodsJson()).stream()
                .map(this::normalizeMethod)
                .filter(SUPPORTED_METHODS::contains)
                .distinct()
                .toList();
        if (configured.isEmpty()) {
            return DEFAULT_METHODS;
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>(configured);
        ordered.add("reverse_dcf");
        if (ordered.size() < 3) {
            DEFAULT_METHODS.stream().limit(3).forEach(ordered::add);
        }
        return new ArrayList<>(ordered);
    }

    private Map<String, Double> resolveDefaultWeights(UsSectorTemplateConfigRecord record, List<String> primaryMethods) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        Map<String, Double> raw = parseWeightMap(record == null ? null : record.defaultWeightsJson());

        for (String method : primaryMethods) {
            Double weight = raw.get(method);
            if (weight != null && weight > 0.0) {
                normalized.put(method, weight);
            }
        }

        if (normalized.isEmpty()) {
            double equalWeight = 1.0 / primaryMethods.size();
            for (String method : primaryMethods) {
                normalized.put(method, equalWeight);
            }
        }
        return normalized;
    }

    private Map<String, Double> resolveParameterMap(Long securityId, String sectorTemplate, LocalDate effectiveDate) {
        LocalDate resolvedDate = effectiveDate == null ? LocalDate.now() : effectiveDate;
        Map<String, Double> values = new LinkedHashMap<>();
        valuationParameterSetRepository.findActiveBySectorTemplate(sectorTemplate, resolvedDate)
                .forEach(record -> putParameter(values, record));
        if (securityId != null) {
            valuationParameterSetRepository.findActiveBySecurityId(securityId, resolvedDate)
                    .forEach(record -> putParameter(values, record));
        }
        return values;
    }

    private void putParameter(Map<String, Double> values, UsValuationParameterRecord record) {
        if (record == null) {
            return;
        }
        Double numeric = parseNumericValue(record.parameterValueJson());
        if (numeric == null) {
            return;
        }
        values.put(normalizeParameterKey(record.parameterType(), record.parameterKey()), numeric);
    }

    private String normalizeParameterKey(String parameterType, String parameterKey) {
        String type = parameterType == null ? "" : parameterType.trim().toLowerCase(Locale.ROOT);
        String key = parameterKey == null ? "" : parameterKey.trim().toLowerCase(Locale.ROOT);
        return type + "." + key;
    }

    private String normalizeMethod(String method) {
        if (method == null) {
            return "";
        }
        return method.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }

    private double resolveMarginOfSafety(UsSectorTemplateConfigRecord record) {
        if (record == null || record.safetyMarginRuleJson() == null || record.safetyMarginRuleJson().isBlank()) {
            return 0.25;
        }
        try {
            JsonNode root = objectMapper.readTree(record.safetyMarginRuleJson());
            JsonNode node = root.path("default_margin_of_safety");
            if (node.isNumber()) {
                return node.asDouble();
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return 0.25;
    }

    private Map<String, Double> normalizeWeights(Map<String, Double> weights, List<String> primaryMethods) {
        double total = weights.values().stream()
                .filter(value -> value != null && value > 0.0)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0.0) {
            double equalWeight = 1.0 / primaryMethods.size();
            Map<String, Double> equal = new LinkedHashMap<>();
            for (String method : primaryMethods) {
                equal.put(method, equalWeight);
            }
            return equal;
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        for (String method : primaryMethods) {
            double raw = Optional.ofNullable(weights.get(method)).orElse(0.0);
            normalized.put(method, raw / total);
        }
        return normalized;
    }

    private Map<String, Double> parseWeightMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, Double> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    String method = normalizeMethod(entry.getKey());
                    if (SUPPORTED_METHODS.contains(method)) {
                        result.put(method, number.doubleValue());
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Double parseNumericValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root.path("value");
            if (node.isNumber()) {
                return node.asDouble();
            }
            if (root.isNumber()) {
                return root.asDouble();
            }
        } catch (Exception ignored) {
            // ignore malformed parameter
        }
        return null;
    }
}
