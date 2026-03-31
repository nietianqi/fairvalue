package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class UsCompanyIrSourceRegistry {
    private final Map<String, TickerIrConfig> configs;

    public UsCompanyIrSourceRegistry(ObjectMapper objectMapper) {
        this.configs = loadConfigs(objectMapper);
    }

    public Optional<TickerIrConfig> findByTicker(String rawTicker) {
        if (rawTicker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(configs.get(rawTicker.trim().toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, TickerIrConfig> loadConfigs(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource("rules/us-company-ir-sources.json").getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, TickerIrConfig.class)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load US company IR source registry.", ex);
        }
    }

    public record TickerIrConfig(
            String ticker,
            String companyName,
            List<IrSourceConfig> sources
    ) {
        public List<IrSourceConfig> safeSources() {
            return sources == null ? Collections.emptyList() : sources;
        }
    }

    public record IrSourceConfig(
            String kind,
            String label,
            String url,
            String defaultDocumentType,
            Integer maxItems
    ) {
        public int safeMaxItems() {
            return maxItems == null || maxItems <= 0 ? 20 : maxItems;
        }
    }
}
