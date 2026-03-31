package com.fairvalue.engine.us;

import com.fairvalue.engine.domain.StockSnapshot;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsExternalValuationParameterService {
    private final UsFredClient usFredClient;
    private final UsDamodaranClient usDamodaranClient;

    public UsExternalValuationParameterService(
            UsFredClient usFredClient,
            UsDamodaranClient usDamodaranClient
    ) {
        this.usFredClient = usFredClient;
        this.usDamodaranClient = usDamodaranClient;
    }

    public ExternalParameterSnapshot resolve(StockSnapshot snapshot, UsSecurityMaster security) {
        Map<String, Double> parameters = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();

        UsDamodaranClient.DamodaranErpSnapshot erpSnapshot = usDamodaranClient.fetchErpSnapshot().orElse(null);
        UsFredClient.FredSeriesObservation riskFreeObservation = usFredClient.fetchRiskFreeRate().orElse(null);
        UsDamodaranClient.DamodaranIndustrySnapshot industrySnapshot = usDamodaranClient.fetchIndustrySnapshot(
                security == null ? null : security.sectorTemplate(),
                security == null ? null : security.industry(),
                security == null ? null : security.sector()
        ).orElse(null);

        double marketCap = impliedMarketCap(snapshot, security);
        double riskFreeRate = riskFreeObservation != null
                ? riskFreeObservation.value()
                : erpSnapshot != null ? erpSnapshot.treasuryRate() : Double.NaN;
        double erp = erpSnapshot == null ? Double.NaN : erpSnapshot.impliedErp();
        double beta = industrySnapshot == null || industrySnapshot.beta() == null ? Double.NaN : industrySnapshot.beta();
        double sizePremium = sizePremium(marketCap);

        if (Double.isFinite(riskFreeRate) && riskFreeRate > 0.0) {
            parameters.put("wacc.rf", riskFreeRate);
            sources.put("wacc.rf", riskFreeObservation != null ? riskFreeObservation.sourceLabel() : erpSnapshot.sourceLabel());
        }
        if (Double.isFinite(erp) && erp > 0.0) {
            parameters.put("wacc.erp", erp);
            sources.put("wacc.erp", erpSnapshot.sourceLabel());
        }
        if (Double.isFinite(beta) && beta > 0.0) {
            parameters.put("wacc.beta", beta);
            sources.put("wacc.beta", "damodaran_beta:" + industrySnapshot.matchedIndustry());
        }
        parameters.put("wacc.size_premium", sizePremium);
        sources.put("wacc.size_premium", "fairvalue_market_cap_band");

        if (Double.isFinite(riskFreeRate) && Double.isFinite(erp) && Double.isFinite(beta)) {
            double dynamicWacc = clamp(riskFreeRate + (beta * erp) + sizePremium, 0.075, 0.16);
            parameters.put("wacc.base", dynamicWacc);
            parameters.put("wacc.floor", clamp(dynamicWacc - 0.01, 0.065, 0.15));
            parameters.put("wacc.ceiling", clamp(dynamicWacc + 0.025, 0.085, 0.20));
            sources.put("wacc.base", "fred+damodaran");
            sources.put("wacc.floor", "derived_from_wacc_base");
            sources.put("wacc.ceiling", "derived_from_wacc_base");
        }

        if (Double.isFinite(riskFreeRate) && riskFreeRate > 0.0) {
            double terminalBase = clamp(Math.min(riskFreeRate * 0.70, 0.032), 0.018, 0.032);
            parameters.put("terminal_growth.base", terminalBase);
            parameters.put("terminal_growth.floor", clamp(terminalBase - 0.005, 0.012, 0.030));
            parameters.put("terminal_growth.ceiling", clamp(terminalBase + 0.005, 0.020, 0.038));
            sources.put("terminal_growth.base", riskFreeObservation != null ? riskFreeObservation.sourceLabel() : "damodaran_histimpl");
            sources.put("terminal_growth.floor", "derived_from_terminal_growth_base");
            sources.put("terminal_growth.ceiling", "derived_from_terminal_growth_base");
        }

        if (industrySnapshot != null && industrySnapshot.trailingPe() != null && industrySnapshot.trailingPe() > 0.0) {
            parameters.put("relative.target_pe", clamp(industrySnapshot.trailingPe(), 6.0, 80.0));
            sources.put("relative.target_pe", "damodaran_pe:" + industrySnapshot.matchedIndustry());
        }
        if (industrySnapshot != null && industrySnapshot.evEbitda() != null && industrySnapshot.evEbitda() > 0.0) {
            parameters.put("relative.target_ev_ebitda", clamp(industrySnapshot.evEbitda(), 4.0, 40.0));
            sources.put("relative.target_ev_ebitda", "damodaran_ev_ebitda:" + industrySnapshot.matchedIndustry());
        }

        return new ExternalParameterSnapshot(parameters, sources);
    }

    private double impliedMarketCap(StockSnapshot snapshot, UsSecurityMaster security) {
        if (snapshot == null) {
            return 0.0;
        }
        double price = snapshot.price();
        double fallbackShares = switch (security == null ? "" : safe(security.sectorTemplate())) {
            case "us_tech_compounder" -> 8_000_000_000.0;
            case "us_general_quality" -> 4_000_000_000.0;
            case "us_cyclical" -> 2_500_000_000.0;
            default -> 3_000_000_000.0;
        };
        return Math.max(price, 0.0) * fallbackShares;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double sizePremium(double marketCap) {
        if (marketCap <= 2_000_000_000d) {
            return 0.020;
        }
        if (marketCap <= 10_000_000_000d) {
            return 0.015;
        }
        if (marketCap <= 50_000_000_000d) {
            return 0.010;
        }
        if (marketCap <= 200_000_000_000d) {
            return 0.005;
        }
        return 0.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ExternalParameterSnapshot(
            Map<String, Double> parameters,
            Map<String, String> sources
    ) {
        public List<String> appliedSources() {
            return sources.values().stream().distinct().toList();
        }
    }
}
