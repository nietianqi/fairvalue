package com.fairvalue.engine.us;

import com.fairvalue.engine.api.dto.us.UsMethodOutput;
import com.fairvalue.engine.api.dto.us.UsValuationReportResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UsValuationDiagnosticsService {
    private final UsSecurityMasterService usSecurityMasterService;
    private final UsValuationReadService usValuationReadService;

    public UsValuationDiagnosticsService(
            UsSecurityMasterService usSecurityMasterService,
            UsValuationReadService usValuationReadService
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.usValuationReadService = usValuationReadService;
    }

    public ValuationDiagnostics diagnostics(String rawTicker) {
        String ticker = normalizeTicker(rawTicker);
        UsSecurityMaster security = usSecurityMasterService.findByTicker(ticker).orElse(null);
        UsStoredValuationSnapshotRecord snapshot = usValuationReadService.findLatestSnapshot(ticker).orElse(null);
        UsValuationReportResponse report = usValuationReadService.findLatestReport(ticker).orElse(null);
        Map<String, Object> attribution = report == null || report.decision() == null || report.decision().sourceAttribution() == null
                ? Map.of()
                : report.decision().sourceAttribution();
        UsMethodOutput dcf = report == null || report.valuationBreakdown() == null
                ? null
                : report.valuationBreakdown().stream()
                .filter(method -> "dcf".equalsIgnoreCase(method.method()))
                .findFirst()
                .orElse(null);

        return new ValuationDiagnostics(
                ticker,
                security == null ? ticker : security.companyName(),
                security == null ? null : security.sector(),
                security == null ? null : security.industry(),
                security == null ? null : security.companyType(),
                security == null ? null : security.sectorTemplate(),
                snapshot == null ? null : snapshot.dataVersion(),
                stringValue(attribution, "price_source"),
                snapshot == null ? null : snapshot.priceAsOf(),
                snapshot == null ? null : snapshot.priceFreshnessDays(),
                snapshot == null ? null : snapshot.priceSourceType(),
                snapshot != null && snapshot.rankable(),
                snapshot == null ? null : snapshot.valuationStatus(),
                snapshot == null ? null : snapshot.exclusionReason(),
                stringValue(attribution, "industryMatchSource", "industry_match_source"),
                doubleValue(attribution, "industryMatchConfidence", "industry_match_confidence"),
                booleanValue(attribution, "industryFallbackUsed", "industry_fallback_used"),
                stringValue(attribution, "relative_source_mode"),
                stringList(attribution, "peer_set_tickers"),
                dcf == null ? null : dcf.methodStatus(),
                dcf == null ? null : dcf.weight(),
                dcf != null && dcf.outlierTrimmed(),
                dcf != null && dcf.weightAdjustedByDataQuality()
        );
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim().toUpperCase(Locale.ROOT).replace(".US", "").replace(".", "-");
    }

    private String stringValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private Double doubleValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private boolean booleanValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
        }
        return false;
    }

    private List<String> stringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public record ValuationDiagnostics(
            String ticker,
            String companyName,
            String sector,
            String industry,
            String companyType,
            String sectorTemplate,
            String dataVersion,
            String priceSource,
            LocalDate priceAsOf,
            Integer priceFreshnessDays,
            String priceSourceType,
            boolean rankable,
            String valuationStatus,
            String exclusionReason,
            String industryMatchSource,
            Double industryMatchConfidence,
            boolean industryFallbackUsed,
            String peerSelectionMode,
            List<String> peerSetTickers,
            String dcfMethodStatus,
            Double dcfWeight,
            boolean dcfOutlierTrimmed,
            boolean dcfWeightAdjustedByDataQuality
    ) {
    }
}
