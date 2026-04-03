package com.fairvalue.engine.us;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class UsExternalSourceStatusService {
    private final UsFredClient usFredClient;
    private final UsFredProperties usFredProperties;
    private final UsLongbridgeClient usLongbridgeClient;
    private final UsLongbridgeProperties usLongbridgeProperties;
    private final UsDamodaranClient usDamodaranClient;
    private final UsDamodaranProperties usDamodaranProperties;
    private final UsSimfinClient usSimfinClient;
    private final UsSimfinProperties usSimfinProperties;

    public UsExternalSourceStatusService(
            UsFredClient usFredClient,
            UsFredProperties usFredProperties,
            UsLongbridgeClient usLongbridgeClient,
            UsLongbridgeProperties usLongbridgeProperties,
            UsDamodaranClient usDamodaranClient,
            UsDamodaranProperties usDamodaranProperties,
            UsSimfinClient usSimfinClient,
            UsSimfinProperties usSimfinProperties
    ) {
        this.usFredClient = usFredClient;
        this.usFredProperties = usFredProperties;
        this.usLongbridgeClient = usLongbridgeClient;
        this.usLongbridgeProperties = usLongbridgeProperties;
        this.usDamodaranClient = usDamodaranClient;
        this.usDamodaranProperties = usDamodaranProperties;
        this.usSimfinClient = usSimfinClient;
        this.usSimfinProperties = usSimfinProperties;
    }

    public ExternalSourceStatus status(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        UsFredClient.FredSeriesObservation riskFree = usFredClient.fetchRiskFreeRate().orElse(null);
        UsFredClient.FredSeriesObservation policyRate = usFredClient.fetchPolicyRate().orElse(null);
        UsLongbridgeClient.UsLongbridgeMarketData longbridgeMarketData = usLongbridgeClient.fetchMarketData(normalizedTicker).orElse(null);
        UsDamodaranClient.DamodaranErpSnapshot damodaranErp = usDamodaranClient.fetchErpSnapshot().orElse(null);
        UsSimfinClient.SimfinStatus simfinStatus = usSimfinClient.probe();
        UsSimfinClient.SimfinCompanyRecord companyRecord = usSimfinClient.fetchCompanyRecord(normalizedTicker).orElse(null);

        return new ExternalSourceStatus(
                Instant.now(),
                normalizedTicker,
                new FredStatus(
                        usFredProperties.isEnabled(),
                        usFredProperties.getApiKey() != null && !usFredProperties.getApiKey().isBlank(),
                        resolveFredStatus(riskFree, policyRate),
                        resolveFredDetail(riskFree, policyRate),
                        riskFree,
                        policyRate
                ),
                new LongbridgeStatus(
                        usLongbridgeProperties.isEnabled(),
                        usLongbridgeProperties.isConfigured(),
                        resolveLongbridgeStatus(longbridgeMarketData),
                        resolveLongbridgeDetail(longbridgeMarketData),
                        longbridgeMarketData == null ? null : longbridgeMarketData.symbolFull(),
                        longbridgeMarketData == null ? null : longbridgeMarketData.tradeDate()
                ),
                new DamodaranStatus(
                        usDamodaranProperties.isEnabled(),
                        resolveDamodaranStatus(damodaranErp),
                        resolveDamodaranDetail(damodaranErp),
                        damodaranErp == null ? null : damodaranErp.asOf(),
                        damodaranErp == null ? null : damodaranErp.sourceLabel()
                ),
                new SimfinStatus(
                        usSimfinProperties.isEnabled(),
                        usSimfinProperties.getApiKey() != null && !usSimfinProperties.getApiKey().isBlank(),
                        simfinStatus.authenticated(),
                        normalizeStatus(simfinStatus.status(), simfinStatus.detail()),
                        simfinStatus.detail(),
                        companyRecord
                )
        );
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker == null ? "" : rawTicker.trim().toUpperCase(java.util.Locale.ROOT).replace(".US", "").replace(".", "-");
    }

    private String resolveFredStatus(
            UsFredClient.FredSeriesObservation riskFree,
            UsFredClient.FredSeriesObservation policyRate
    ) {
        if (!usFredProperties.isEnabled()) {
            return "disabled";
        }
        if (riskFree != null || policyRate != null) {
            return "ok";
        }
        if (usFredProperties.getApiKey() == null || usFredProperties.getApiKey().isBlank()) {
            return "unauthenticated";
        }
        return "error";
    }

    private String resolveFredDetail(
            UsFredClient.FredSeriesObservation riskFree,
            UsFredClient.FredSeriesObservation policyRate
    ) {
        if (!usFredProperties.isEnabled()) {
            return null;
        }
        if (riskFree != null || policyRate != null) {
            return "observation_loaded";
        }
        if (usFredProperties.getApiKey() == null || usFredProperties.getApiKey().isBlank()) {
            return "api_key_missing";
        }
        return "observation_unavailable";
    }

    private String resolveLongbridgeStatus(UsLongbridgeClient.UsLongbridgeMarketData marketData) {
        if (!usLongbridgeProperties.isEnabled()) {
            return "disabled";
        }
        if (!usLongbridgeProperties.isConfigured()) {
            return "unauthenticated";
        }
        return marketData != null && marketData.hasQuote() ? "ok" : "error";
    }

    private String resolveLongbridgeDetail(UsLongbridgeClient.UsLongbridgeMarketData marketData) {
        if (!usLongbridgeProperties.isEnabled()) {
            return null;
        }
        if (!usLongbridgeProperties.isConfigured()) {
            return "credentials_missing";
        }
        return marketData != null && marketData.hasQuote()
                ? "quote_loaded"
                : "quote_unavailable";
    }

    private String resolveDamodaranStatus(UsDamodaranClient.DamodaranErpSnapshot erpSnapshot) {
        if (!usDamodaranProperties.isEnabled()) {
            return "disabled";
        }
        if (erpSnapshot == null) {
            return "error";
        }
        if ("damodaran_static_fallback".equalsIgnoreCase(erpSnapshot.sourceLabel())) {
            return "warning";
        }
        return "ok";
    }

    private String resolveDamodaranDetail(UsDamodaranClient.DamodaranErpSnapshot erpSnapshot) {
        if (!usDamodaranProperties.isEnabled()) {
            return null;
        }
        if (erpSnapshot == null) {
            return "erp_snapshot_unavailable";
        }
        if ("damodaran_static_fallback".equalsIgnoreCase(erpSnapshot.sourceLabel())) {
            return "erp_snapshot_fallback";
        }
        return "erp_snapshot_loaded";
    }

    private String normalizeStatus(String rawStatus, String detail) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "error";
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "error", "disabled", "unauthenticated", "timeout" -> normalized;
            default -> {
                if (normalized.contains("timeout") || (detail != null && detail.toLowerCase(Locale.ROOT).contains("timeout"))) {
                    yield "timeout";
                }
                if (normalized.contains("auth") || normalized.contains("401") || normalized.contains("403")) {
                    yield "unauthenticated";
                }
                yield "error";
            }
        };
    }

    public record ExternalSourceStatus(
            Instant asOf,
            String ticker,
            FredStatus fred,
            LongbridgeStatus longbridge,
            DamodaranStatus damodaran,
            SimfinStatus simfin
    ) {
    }

    public record FredStatus(
            boolean enabled,
            boolean apiKeyPresent,
            String status,
            String detail,
            UsFredClient.FredSeriesObservation riskFreeObservation,
            UsFredClient.FredSeriesObservation policyRateObservation
    ) {
    }

    public record LongbridgeStatus(
            boolean enabled,
            boolean configured,
            String status,
            String detail,
            String symbolFull,
            java.time.LocalDate tradeDate
    ) {
    }

    public record DamodaranStatus(
            boolean enabled,
            String status,
            String detail,
            java.time.LocalDate erpAsOf,
            String erpSource
    ) {
    }

    public record SimfinStatus(
            boolean enabled,
            boolean apiKeyPresent,
            boolean authenticated,
            String status,
            String detail,
            UsSimfinClient.SimfinCompanyRecord companyRecord
    ) {
    }
}
