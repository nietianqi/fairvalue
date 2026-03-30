package com.fairvalue.engine.service;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketDataService {
    private final Map<Market, Map<String, StockSnapshot>> data;

    public MarketDataService() {
        this.data = new EnumMap<>(Market.class);
        seed();
    }

    public StockSnapshot getSnapshot(Market market, String symbol) {
        String normalized = normalizeSymbol(symbol);
        Map<String, StockSnapshot> marketData = data.getOrDefault(market, Map.of());
        StockSnapshot snapshot = marketData.get(normalized);
        if (snapshot != null) {
            return snapshot;
        }
        return syntheticSnapshot(market, normalized);
    }

    public List<StockSnapshot> listSnapshots(List<Market> markets) {
        List<StockSnapshot> all = new ArrayList<>();
        for (Market market : markets) {
            all.addAll(data.getOrDefault(market, Map.of()).values());
        }
        all.sort(Comparator.comparing(StockSnapshot::symbol));
        return all;
    }

    private void seed() {
        for (Market market : Market.values()) {
            data.put(market, new HashMap<>());
        }

        put(new StockSnapshot(Market.US, "AAPL", "USD", "Apple Inc.", "Technology", 210.35,
                fundamentals(0.08, 0.27, 0.09, 0.025, 0.31, 29.5, 44.0, 20.2, 0.005, 0.026, -0.02, 0.10, 0.25, 0.92, 0.0, 0.032, 0.18, 7, 0.95, 0.98, 0.12, 0.84, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "MSFT", "USD", "Microsoft Corp.", "Technology", 472.80,
                fundamentals(0.11, 0.32, 0.088, 0.027, 0.37, 33.2, 10.4, 24.8, 0.007, 0.018, -0.01, 0.08, 0.24, 0.93, 0.0, 0.025, 0.17, 8, 0.96, 0.99, 0.10, 0.88, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "NVDA", "USD", "NVIDIA Corp.", "Semiconductors", 1230.00,
                fundamentals(0.34, 0.42, 0.10, 0.030, 0.59, 48.0, 31.0, 36.0, 0.001, 0.007, 0.15, 0.15, 0.72, 0.88, 0.0, 0.038, 0.39, 6, 0.90, 0.95, 0.08, 0.74, true), "2026Q1"));

        put(new StockSnapshot(Market.CN, "600519", "CNY", "Kweichow Moutai", "Consumer Staples", 1735.00,
                fundamentals(0.12, 0.34, 0.10, 0.023, 0.33, 30.5, 10.2, 18.0, 0.018, 0.006, 0.08, 0.20, 0.40, 0.78, 0.0, 0.0, 0.21, 12, 0.88, 0.70, 0.0, 0.72, true), "2026Q1"));
        put(new StockSnapshot(Market.CN, "000858", "CNY", "Wuliangye Yibin", "Consumer Staples", 168.00,
                fundamentals(0.10, 0.25, 0.10, 0.022, 0.28, 21.0, 5.8, 14.5, 0.024, 0.002, 0.05, 0.24, 0.32, 0.74, 0.0, 0.0, 0.24, 11, 0.84, 0.58, 0.0, 0.66, true), "2026Q1"));
        put(new StockSnapshot(Market.CN, "300750", "CNY", "CATL", "Industrials", 235.60,
                fundamentals(0.22, 0.17, 0.11, 0.022, 0.18, 26.5, 5.2, 16.2, 0.006, 0.0, -0.06, 0.42, 0.55, 0.68, 0.0, 0.0, 0.43, 14, 0.80, 0.42, 0.0, 0.60, true), "2026Q1"));

        put(new StockSnapshot(Market.JP, "7203", "JPY", "Toyota Motor", "Automobiles", 3150.0,
                fundamentals(0.06, 0.12, 0.08, 0.018, 0.11, 10.4, 1.20, 6.8, 0.029, 0.013, 0.23, 0.0, 0.0, 0.82, 0.0, 0.0, 0.19, 16, 0.90, 0.55, 0.0, 0.71, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "6758", "JPY", "Sony Group", "Technology", 15780.0,
                fundamentals(0.07, 0.10, 0.085, 0.020, 0.13, 16.8, 2.1, 10.2, 0.007, 0.010, 0.05, 0.0, 0.0, 0.79, 0.0, 0.0, 0.25, 17, 0.88, 0.60, 0.0, 0.69, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "8306", "JPY", "Mitsubishi UFJ", "Financials", 1735.0,
                fundamentals(0.04, 0.0, 0.075, 0.015, 0.095, 11.8, 0.92, 0.0, 0.031, 0.015, 0.12, 0.0, 0.0, 0.76, 0.0, 0.0, 0.29, 15, 0.86, 0.50, 0.0, 0.64, true), "2026Q1"));

        put(new StockSnapshot(Market.HK, "0700.HK", "HKD", "Tencent Holdings", "Technology", 421.20,
                fundamentals(0.10, 0.22, 0.095, 0.022, 0.19, 18.5, 3.2, 11.4, 0.010, 0.024, 0.05, 0.18, 0.22, 0.72, -0.05, 0.0, 0.24, 10, 0.88, 0.62, 0.72, 0.70, true), "2026Q1"));
        put(new StockSnapshot(Market.HK, "0005.HK", "HKD", "HSBC Holdings", "Financials", 66.40,
                fundamentals(0.03, 0.0, 0.08, 0.012, 0.10, 8.9, 0.88, 0.0, 0.072, 0.006, -0.10, 0.10, 0.0, 0.80, -0.08, 0.0, 0.20, 13, 0.90, 0.50, 0.35, 0.68, true), "2026Q1"));
        put(new StockSnapshot(Market.HK, "9988.HK", "HKD", "Alibaba Group HK", "Consumer Discretionary", 89.60,
                fundamentals(0.08, 0.09, 0.10, 0.020, 0.09, 10.3, 1.55, 7.1, 0.0, 0.020, 0.18, 0.22, 0.10, 0.62, -0.10, 0.0, 0.36, 14, 0.82, 0.58, 0.80, 0.57, true), "2026Q1"));
    }

    private void put(StockSnapshot snapshot) {
        data.get(snapshot.market()).put(normalizeSymbol(snapshot.symbol()), snapshot);
    }

    private StockSnapshot syntheticSnapshot(Market market, String symbol) {
        int hash = Math.abs((market.name() + symbol).hashCode());
        double seed = (hash % 1000) / 1000.0;
        double price = 10.0 + (hash % 9000) / 10.0;

        StockFundamentals fundamentals = fundamentals(
                0.04 + seed * 0.18,
                0.06 + seed * 0.18,
                0.08 + seed * 0.04,
                0.015 + seed * 0.015,
                0.06 + seed * 0.18,
                8.0 + seed * 28.0,
                0.7 + seed * 4.5,
                6.0 + seed * 18.0,
                0.005 + seed * 0.05,
                seed * 0.03,
                -0.05 + seed * 0.35,
                seed * 0.6,
                seed * 0.8,
                0.35 + seed * 0.55,
                -0.2 + seed * 0.4,
                seed * 0.06,
                0.15 + seed * 0.35,
                5 + seed * 40,
                0.55 + seed * 0.4,
                0.2 + seed * 0.75,
                seed,
                0.4 + seed * 0.5,
                seed > 0.3
        );

        return new StockSnapshot(
                market,
                symbol,
                market == Market.US ? "USD" : market == Market.JP ? "JPY" : market == Market.HK ? "HKD" : "CNY",
                symbol + " Corp.",
                "General",
                Math.round(price * 100.0) / 100.0,
                fundamentals,
                "synthetic-2026Q1"
        );
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private StockFundamentals fundamentals(
            double revenueGrowth,
            double fcfMargin,
            double wacc,
            double terminalGrowth,
            double roe,
            double pe,
            double pb,
            double evEbitda,
            double dividendYield,
            double buybackYield,
            double netCashToMarketCap,
            double policySensitivity,
            double themePremium,
            double liquidityScore,
            double ahPremiumGap,
            double sbcRatio,
            double earningsVolatility,
            double dataFreshnessDays,
            double dataCompleteness,
            double analystCoverage,
            double chinaExposure,
            double governanceScore,
            boolean positiveFreeCashFlow
    ) {
        return new StockFundamentals(
                revenueGrowth,
                fcfMargin,
                wacc,
                terminalGrowth,
                roe,
                pe,
                pb,
                evEbitda,
                dividendYield,
                buybackYield,
                netCashToMarketCap,
                policySensitivity,
                themePremium,
                liquidityScore,
                ahPremiumGap,
                sbcRatio,
                earningsVolatility,
                dataFreshnessDays,
                dataCompleteness,
                analystCoverage,
                chinaExposure,
                governanceScore,
                positiveFreeCashFlow
        );
    }
}
