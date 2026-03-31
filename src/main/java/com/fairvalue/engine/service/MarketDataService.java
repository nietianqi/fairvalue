package com.fairvalue.engine.service;

import com.fairvalue.engine.cn.CnEastmoneyClient;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.us.UsMarketDataPersistenceService;
import com.fairvalue.engine.us.UsSecClient;
import com.fairvalue.engine.us.UsStooqClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final CnEastmoneyClient cnEastmoneyClient;
    private final UsStooqClient usStooqClient;
    private final UsSecClient usSecClient;
    private final UsMarketDataPersistenceService usMarketDataPersistenceService;

    public MarketDataService(
            CnEastmoneyClient cnEastmoneyClient,
            UsStooqClient usStooqClient,
            UsSecClient usSecClient,
            UsMarketDataPersistenceService usMarketDataPersistenceService
    ) {
        this.cnEastmoneyClient = cnEastmoneyClient;
        this.usStooqClient = usStooqClient;
        this.usSecClient = usSecClient;
        this.usMarketDataPersistenceService = usMarketDataPersistenceService;
        this.data = new EnumMap<>(Market.class);
        seed();
    }

    public StockSnapshot getSnapshot(Market market, String symbol) {
        String normalized = normalizeSymbol(symbol);
        Map<String, StockSnapshot> marketData = data.getOrDefault(market, Map.of());
        StockSnapshot template = marketData.get(normalized);

        if (market == Market.CN) {
            StockSnapshot fallback = template != null ? template : syntheticSnapshot(market, normalized);
            return cnEastmoneyClient.fetchQuote(normalized)
                    .map(quote -> mergeCnSnapshot(fallback, quote))
                    .orElse(fallback);
        }

        if (market == Market.US) {
            StockSnapshot fallback = template != null ? template : syntheticSnapshot(market, normalized);
            UsStooqClient.UsQuote quote = usStooqClient.fetchQuote(normalized).orElse(null);
            UsSecClient.UsSecProfile profile = usSecClient.fetchProfile(normalized).orElse(null);
            if (quote != null || profile != null) {
                StockSnapshot merged = mergeUsSnapshot(fallback, quote, profile);
                usMarketDataPersistenceService.persistLiveSnapshot(normalized, merged, quote, profile);
                return merged;
            }
            return fallback;
        }

        if (template != null) {
            return template;
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

    public StockSnapshot snapshotFromCnUniverseItem(CnEastmoneyClient.CnUniverseItem item) {
        String normalized = normalizeSymbol(item.symbol());
        StockSnapshot template = data.getOrDefault(Market.CN, Map.of()).get(normalized);
        StockSnapshot fallback = template != null ? template : syntheticSnapshot(Market.CN, normalized);
        return mergeCnSnapshot(fallback, item);
    }

    private void seed() {
        for (Market market : Market.values()) {
            data.put(market, new HashMap<>());
        }

        put(new StockSnapshot(Market.US, "AAPL", "USD", "Apple Inc.", "Technology", 210.35,
                fundamentals(0.08, 0.27, 0.09, 0.025, 0.31, 29.5, 44.0, 20.2, 0.005, 0.026, -0.02, 0.10, 0.25, 0.92, 0.0, 0.032, 0.18, 7, 0.95, 0.98, 0.12, 0.84, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "MSFT", "USD", "Microsoft Corp.", "Technology", 472.80,
                fundamentals(0.11, 0.32, 0.088, 0.027, 0.37, 33.2, 10.4, 24.8, 0.007, 0.018, -0.01, 0.08, 0.24, 0.93, 0.0, 0.025, 0.17, 8, 0.96, 0.99, 0.10, 0.88, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "NVDA", "USD", "NVIDIA Corp.", "Semiconductors", 118.50,
                fundamentals(0.34, 0.42, 0.10, 0.030, 0.59, 48.0, 31.0, 36.0, 0.001, 0.007, 0.15, 0.15, 0.72, 0.88, 0.0, 0.038, 0.39, 6, 0.90, 0.95, 0.08, 0.74, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "GOOGL", "USD", "Alphabet Inc.", "Technology", 162.80,
                fundamentals(0.12, 0.26, 0.088, 0.026, 0.22, 21.5, 6.2, 14.8, 0.0, 0.015, 0.08, 0.08, 0.20, 0.94, 0.0, 0.012, 0.22, 7, 0.94, 0.97, 0.15, 0.82, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "AMZN", "USD", "Amazon.com Inc.", "Consumer Discretionary", 196.50,
                fundamentals(0.10, 0.08, 0.092, 0.025, 0.12, 38.0, 9.5, 22.0, 0.0, 0.0, 0.04, 0.10, 0.30, 0.94, 0.0, 0.018, 0.28, 8, 0.93, 0.97, 0.14, 0.80, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "META", "USD", "Meta Platforms Inc.", "Technology", 565.00,
                fundamentals(0.18, 0.35, 0.090, 0.026, 0.28, 26.0, 9.8, 18.5, 0.0, 0.010, 0.06, 0.10, 0.28, 0.91, 0.0, 0.020, 0.25, 7, 0.92, 0.96, 0.12, 0.79, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "TSLA", "USD", "Tesla Inc.", "Consumer Discretionary", 275.00,
                fundamentals(0.15, 0.06, 0.10, 0.030, 0.08, 65.0, 12.0, 38.0, 0.0, 0.0, 0.10, 0.15, 0.55, 0.87, 0.0, 0.030, 0.48, 8, 0.86, 0.90, 0.08, 0.68, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "BRK-B", "USD", "Berkshire Hathaway B", "Financials", 490.00,
                fundamentals(0.05, 0.12, 0.075, 0.020, 0.10, 11.0, 1.60, 7.5, 0.0, 0.0, 0.20, 0.05, 0.08, 0.90, 0.0, 0.0, 0.14, 10, 0.92, 0.95, 0.05, 0.88, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "JPM", "USD", "JPMorgan Chase", "Financials", 240.00,
                fundamentals(0.06, 0.0, 0.080, 0.018, 0.14, 13.5, 2.10, 0.0, 0.022, 0.012, -0.05, 0.05, 0.08, 0.91, 0.0, 0.0, 0.20, 9, 0.92, 0.96, 0.08, 0.85, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "V", "USD", "Visa Inc.", "Financials", 345.00,
                fundamentals(0.10, 0.55, 0.082, 0.024, 0.48, 30.0, 15.5, 22.0, 0.008, 0.022, -0.08, 0.05, 0.15, 0.92, 0.0, 0.005, 0.14, 7, 0.95, 0.97, 0.06, 0.87, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "MA", "USD", "Mastercard Inc.", "Financials", 530.00,
                fundamentals(0.11, 0.53, 0.083, 0.024, 0.50, 32.0, 60.0, 23.5, 0.006, 0.020, -0.12, 0.05, 0.15, 0.91, 0.0, 0.004, 0.15, 7, 0.95, 0.97, 0.06, 0.86, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "JNJ", "USD", "Johnson & Johnson", "Healthcare", 155.00,
                fundamentals(0.04, 0.20, 0.078, 0.020, 0.17, 14.5, 5.2, 10.0, 0.033, 0.008, -0.03, 0.05, 0.10, 0.90, 0.0, 0.003, 0.14, 8, 0.92, 0.94, 0.08, 0.86, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "UNH", "USD", "UnitedHealth Group", "Healthcare", 485.00,
                fundamentals(0.09, 0.07, 0.082, 0.022, 0.22, 18.0, 4.8, 12.5, 0.016, 0.010, -0.06, 0.05, 0.12, 0.91, 0.0, 0.004, 0.18, 8, 0.93, 0.95, 0.06, 0.84, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "LLY", "USD", "Eli Lilly", "Healthcare", 780.00,
                fundamentals(0.35, 0.28, 0.092, 0.028, 0.30, 55.0, 40.0, 38.0, 0.007, 0.006, -0.05, 0.08, 0.25, 0.90, 0.0, 0.008, 0.28, 7, 0.90, 0.93, 0.05, 0.78, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "WMT", "USD", "Walmart Inc.", "Consumer Staples", 92.00,
                fundamentals(0.05, 0.04, 0.076, 0.020, 0.13, 30.0, 7.5, 17.0, 0.013, 0.008, -0.04, 0.05, 0.12, 0.92, 0.0, 0.002, 0.14, 7, 0.93, 0.95, 0.10, 0.83, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "XOM", "USD", "ExxonMobil Corp.", "Energy", 112.00,
                fundamentals(0.04, 0.10, 0.078, 0.018, 0.12, 14.0, 2.20, 8.5, 0.038, 0.012, -0.02, 0.10, 0.15, 0.90, 0.0, 0.002, 0.22, 8, 0.91, 0.93, 0.08, 0.82, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "CVX", "USD", "Chevron Corp.", "Energy", 152.00,
                fundamentals(0.03, 0.09, 0.078, 0.018, 0.10, 13.0, 1.80, 7.8, 0.042, 0.015, 0.02, 0.10, 0.14, 0.89, 0.0, 0.002, 0.22, 8, 0.91, 0.93, 0.07, 0.81, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "PG", "USD", "Procter & Gamble", "Consumer Staples", 168.00,
                fundamentals(0.04, 0.20, 0.077, 0.020, 0.26, 26.0, 8.5, 18.0, 0.025, 0.010, -0.10, 0.05, 0.12, 0.91, 0.0, 0.003, 0.12, 7, 0.93, 0.95, 0.06, 0.86, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "KO", "USD", "Coca-Cola Co.", "Consumer Staples", 68.00,
                fundamentals(0.04, 0.22, 0.075, 0.019, 0.38, 23.0, 10.0, 16.0, 0.030, 0.008, -0.28, 0.05, 0.12, 0.90, 0.0, 0.002, 0.11, 7, 0.93, 0.95, 0.06, 0.85, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "HD", "USD", "Home Depot Inc.", "Consumer Discretionary", 375.00,
                fundamentals(0.04, 0.12, 0.082, 0.022, 0.70, 24.0, -25.0, 15.5, 0.022, 0.015, -0.30, 0.08, 0.15, 0.91, 0.0, 0.005, 0.18, 7, 0.93, 0.95, 0.08, 0.84, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "ABBV", "USD", "AbbVie Inc.", "Healthcare", 185.00,
                fundamentals(0.08, 0.28, 0.082, 0.022, 0.25, 17.0, -8.0, 12.5, 0.035, 0.010, -0.20, 0.08, 0.15, 0.89, 0.0, 0.004, 0.18, 8, 0.91, 0.93, 0.06, 0.82, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "BAC", "USD", "Bank of America", "Financials", 41.00,
                fundamentals(0.04, 0.0, 0.078, 0.017, 0.10, 13.0, 1.15, 0.0, 0.025, 0.008, -0.08, 0.05, 0.08, 0.91, 0.0, 0.0, 0.22, 9, 0.91, 0.94, 0.08, 0.83, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "COST", "USD", "Costco Wholesale", "Consumer Staples", 920.00,
                fundamentals(0.07, 0.03, 0.080, 0.022, 0.26, 52.0, 14.0, 32.0, 0.006, 0.008, -0.02, 0.05, 0.12, 0.92, 0.0, 0.002, 0.15, 7, 0.93, 0.95, 0.06, 0.86, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "AVGO", "USD", "Broadcom Inc.", "Semiconductors", 195.00,
                fundamentals(0.20, 0.38, 0.092, 0.027, 0.28, 35.0, 8.5, 24.0, 0.013, 0.010, -0.12, 0.12, 0.30, 0.89, 0.0, 0.022, 0.28, 7, 0.90, 0.93, 0.07, 0.77, true), "2026Q1"));
        put(new StockSnapshot(Market.US, "AMD", "USD", "Advanced Micro Devices", "Semiconductors", 112.00,
                fundamentals(0.14, 0.15, 0.095, 0.028, 0.05, 45.0, 4.5, 28.0, 0.0, 0.0, 0.06, 0.12, 0.35, 0.87, 0.0, 0.028, 0.38, 7, 0.88, 0.92, 0.07, 0.72, true), "2026Q1"));

        seedUsUniverse();

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
        put(new StockSnapshot(Market.JP, "9983", "JPY", "Fast Retailing", "Consumer Discretionary", 43800.0,
                fundamentals(0.09, 0.11, 0.082, 0.019, 0.14, 24.5, 3.8, 14.6, 0.009, 0.004, -0.03, 0.0, 0.0, 0.72, 0.0, 0.0, 0.22, 14, 0.87, 0.58, 0.0, 0.66, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "8058", "JPY", "Mitsubishi Corp.", "Industrials", 3580.0,
                fundamentals(0.05, 0.09, 0.080, 0.018, 0.12, 10.8, 1.25, 7.6, 0.028, 0.019, 0.18, 0.0, 0.0, 0.74, 0.0, 0.0, 0.24, 15, 0.89, 0.53, 0.0, 0.70, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "9432", "JPY", "NTT", "Telecom", 183.0,
                fundamentals(0.03, 0.13, 0.073, 0.014, 0.11, 12.5, 1.50, 8.0, 0.032, 0.017, -0.05, 0.0, 0.0, 0.80, 0.0, 0.0, 0.14, 12, 0.92, 0.48, 0.0, 0.72, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "2914", "JPY", "Japan Tobacco", "Consumer Staples", 4050.0,
                fundamentals(0.02, 0.21, 0.078, 0.013, 0.16, 15.2, 2.25, 9.4, 0.055, 0.007, -0.08, 0.0, 0.0, 0.68, 0.0, 0.0, 0.17, 13, 0.90, 0.44, 0.0, 0.61, true), "2026Q1"));
        put(new StockSnapshot(Market.JP, "6501", "JPY", "Hitachi", "Industrials", 15890.0,
                fundamentals(0.08, 0.09, 0.083, 0.018, 0.12, 19.3, 2.45, 12.1, 0.012, 0.006, 0.02, 0.0, 0.0, 0.73, 0.0, 0.0, 0.27, 16, 0.86, 0.57, 0.0, 0.68, true), "2026Q1"));

        put(new StockSnapshot(Market.HK, "0700.HK", "HKD", "Tencent Holdings", "Technology", 421.20,
                fundamentals(0.10, 0.22, 0.095, 0.022, 0.19, 18.5, 3.2, 11.4, 0.010, 0.024, 0.05, 0.18, 0.22, 0.72, -0.05, 0.0, 0.24, 10, 0.88, 0.62, 0.72, 0.70, true), "2026Q1"));
        put(new StockSnapshot(Market.HK, "0005.HK", "HKD", "HSBC Holdings", "Financials", 66.40,
                fundamentals(0.03, 0.0, 0.08, 0.012, 0.10, 8.9, 0.88, 0.0, 0.072, 0.006, -0.10, 0.10, 0.0, 0.80, -0.08, 0.0, 0.20, 13, 0.90, 0.50, 0.35, 0.68, true), "2026Q1"));
        put(new StockSnapshot(Market.HK, "9988.HK", "HKD", "Alibaba Group HK", "Consumer Discretionary", 89.60,
                fundamentals(0.08, 0.09, 0.10, 0.020, 0.09, 10.3, 1.55, 7.1, 0.0, 0.020, 0.18, 0.22, 0.10, 0.62, -0.10, 0.0, 0.36, 14, 0.82, 0.58, 0.80, 0.57, true), "2026Q1"));
    }

    /**
     * Seeds additional US stock symbols using synthetic fallback data.
     * Real prices and fundamentals are fetched from Stooq + SEC EDGAR lazily
     * via getSnapshot() when each stock is first accessed in the discovery flow.
     */
    private void seedUsUniverse() {
        // S&P 500 major stocks not yet explicitly seeded (real data from Stooq+SEC overrides)
        List<String> symbols = List.of(
                // Tech / Software / Internet
                "NFLX", "ORCL", "CRM", "ADBE", "INTU", "NOW", "UBER", "PLTR", "SNOW",
                "PANW", "CRWD", "NET", "ZS", "DDOG", "MDB", "TTD",
                // Semiconductors
                "INTC", "QCOM", "TXN", "MU", "AMAT", "KLAC", "LRCX", "MRVL",
                // Financials / Banks
                "GS", "MS", "WFC", "C", "AXP", "BLK", "SCHW", "SPGI", "MCO", "CME", "ICE",
                "CB", "PNC", "USB", "TFC",
                // Healthcare / Biotech
                "TMO", "ABT", "MDT", "ISRG", "BMY", "MRK", "PFE", "AMGN", "GILD",
                "REGN", "VRTX", "BIIB", "CI", "HUM", "ELV",
                // Consumer / Retail
                "MCD", "SBUX", "NKE", "TGT", "LOW", "TJX", "BKNG", "MAR", "HLT",
                // Industrials / Defense / Aerospace
                "HON", "GE", "CAT", "DE", "BA", "RTX", "LMT", "NOC", "UPS", "FDX", "EMR",
                // Telecom / Media
                "T", "VZ", "TMUS", "DIS", "CMCSA", "WBD",
                // Energy
                "SLB", "EOG", "COP", "PSX", "OXY",
                // Consumer Staples
                "PEP", "MDLZ", "CL", "GIS", "HSY",
                // REITs / Infrastructure
                "AMT", "PLD", "EQIX", "CCI", "O",
                // Insurance / Misc Financials
                "MMC", "TRV", "ALL", "MET", "PRU"
        );
        for (String symbol : symbols) {
            String normalized = normalizeSymbol(symbol);
            if (!data.get(Market.US).containsKey(normalized)) {
                data.get(Market.US).put(normalized, syntheticSnapshot(Market.US, normalized));
            }
        }
    }

    private void put(StockSnapshot snapshot) {
        data.get(snapshot.market()).put(normalizeSymbol(snapshot.symbol()), snapshot);
    }

    private StockSnapshot mergeCnSnapshot(StockSnapshot template, CnEastmoneyClient.CnLiveQuote quote) {
        return mergeCnSnapshot(
                template,
                quote.symbol(),
                quote.name(),
                quote.industry(),
                quote.price(),
                quote.pe(),
                quote.pb(),
                quote.roe(),
                quote.marketCap(),
                null,
                null,
                quote.dataVersion()
        );
    }

    private StockSnapshot mergeCnSnapshot(StockSnapshot template, CnEastmoneyClient.CnUniverseItem item) {
        return mergeCnSnapshot(
                template,
                item.symbol(),
                item.name(),
                template.industry(),
                item.price(),
                item.pe(),
                item.pb(),
                null,
                item.marketCap(),
                item.changePercent(),
                item.turnoverRate(),
                item.dataVersion()
        );
    }

    private StockSnapshot mergeCnSnapshot(
            StockSnapshot template,
            String symbol,
            String companyName,
            String industry,
            double price,
            Double pe,
            Double pb,
            Double roe,
            Double marketCap,
            Double dailyChange,
            Double turnoverRate,
            String dataVersion
    ) {
        StockFundamentals base = template.fundamentals();
        double resolvedPe = positiveOrDefault(pe, base.pe());
        double resolvedPb = positiveOrDefault(pb, base.pb());
        double resolvedRoe = ratioOrDefault(roe, base.roe());
        double capScore = marketCapScore(marketCap, base.liquidityScore());
        double resolvedLiquidity = turnoverRate != null
                ? clamp(turnoverRate * 4.0 + capScore * 0.35, 0.25, 0.98)
                : clamp(base.liquidityScore() * 0.70 + capScore * 0.30, 0.20, 0.95);
        double resolvedVolatility = dailyChange != null
                ? clamp(Math.abs(dailyChange) * 0.9 + base.earningsVolatility() * 0.65, 0.08, 0.65)
                : base.earningsVolatility();
        double resolvedDataCompleteness = completenessScore(pe, pb, roe, turnoverRate, base.dataCompleteness());
        double resolvedAnalystCoverage = clamp(base.analystCoverage() * 0.60 + capScore * 0.40, 0.18, 0.95);
        double resolvedGovernance = clamp(base.governanceScore() * 0.75 + capScore * 0.25, 0.35, 0.96);
        boolean resolvedPositiveFcf = base.positiveFreeCashFlow() || (resolvedPe > 0 && resolvedRoe > 0.06);
        double resolvedEvEbitda = clamp(base.evEbitda() * 0.60 + impliedEvEbitda(resolvedPe) * 0.40, 4.0, 26.0);

        StockFundamentals merged = new StockFundamentals(
                base.revenueGrowth(),
                base.fcfMargin(),
                base.wacc(),
                base.terminalGrowth(),
                resolvedRoe,
                resolvedPe,
                resolvedPb,
                resolvedEvEbitda,
                base.dividendYield(),
                base.buybackYield(),
                base.netCashToMarketCap(),
                base.policySensitivity(),
                base.themePremium(),
                resolvedLiquidity,
                base.ahPremiumGap(),
                base.sbcRatio(),
                resolvedVolatility,
                0.2,
                resolvedDataCompleteness,
                resolvedAnalystCoverage,
                base.chinaExposure(),
                resolvedGovernance,
                resolvedPositiveFcf
        );

        String resolvedIndustry = industry == null || industry.isBlank() ? template.industry() : industry;
        String resolvedName = companyName == null || companyName.isBlank() ? template.companyName() : companyName;
        return new StockSnapshot(
                Market.CN,
                symbol,
                "CNY",
                resolvedName,
                resolvedIndustry,
                round(price),
                merged,
                dataVersion == null || dataVersion.isBlank() ? template.dataVersion() : dataVersion
        );
    }

    private StockSnapshot mergeUsSnapshot(
            StockSnapshot template,
            UsStooqClient.UsQuote quote,
            UsSecClient.UsSecProfile profile
    ) {
        StockFundamentals base = template.fundamentals();

        double resolvedPrice = quote != null && quote.close() > 0
                ? quote.close()
                : template.price();
        double resolvedMarketCap = resolveUsMarketCap(resolvedPrice, profile, base);
        double resolvedRevenueGrowth = revenueGrowth(profile, base.revenueGrowth());
        double resolvedFcfMargin = clamp(freeCashFlowMargin(profile, base.fcfMargin()), -0.20, 0.45);
        double resolvedRoe = clamp(orFallback(ratio(profile == null ? null : profile.annualNetIncome(), profile == null ? null : profile.equity()), base.roe()), -0.30, 0.80);
        double resolvedPe = resolveUsPe(resolvedMarketCap, profile, base.pe());
        double resolvedPb = clamp(orFallback(ratio(resolvedMarketCap, profile == null ? null : profile.equity()), base.pb()), 0.4, 45.0);
        double resolvedEvEbitda = resolveUsEvEbitda(resolvedMarketCap, profile, base.evEbitda(), resolvedPe);
        double resolvedNetCash = clamp(orFallback(netCashToMarketCap(resolvedMarketCap, profile), base.netCashToMarketCap()), -0.45, 0.55);
        double resolvedSbcRatio = clamp(orFallback(ratio(profile == null ? null : profile.stockBasedCompensation(), profile == null ? null : profile.annualRevenue()), base.sbcRatio()), 0.0, 0.18);

        double capScore = marketCapScore(resolvedMarketCap > 0 ? resolvedMarketCap : null, base.liquidityScore());
        double volumeScore = volumeScore(quote == null ? null : quote.volume(), base.liquidityScore());
        double resolvedLiquidity = clamp(base.liquidityScore() * 0.25 + capScore * 0.35 + volumeScore * 0.40, 0.18, 0.99);
        double resolvedVolatility = quote != null
                ? clamp(base.earningsVolatility() * 0.55 + Math.abs(quote.dailyChange()) * 2.2, 0.08, 0.72)
                : base.earningsVolatility();
        double resolvedFreshness = usFreshnessDays(profile, base.dataFreshnessDays());
        double resolvedCompleteness = clamp(
                profile != null
                        ? base.dataCompleteness() * 0.25 + profile.dataCompleteness() * 0.75 + (quote != null ? 0.03 : 0.0)
                        : base.dataCompleteness(),
                0.45,
                0.98
        );
        double resolvedAnalystCoverage = clamp(
                base.analystCoverage() * 0.22 + capScore * 0.48 + resolvedCompleteness * 0.30,
                0.18,
                0.98
        );
        double resolvedGovernance = clamp(
                base.governanceScore() * 0.42
                        + resolvedCompleteness * 0.28
                        + capScore * 0.18
                        + (profile != null && profile.hasRecent8k() ? -0.02 : 0.03),
                0.35,
                0.97
        );
        double resolvedWacc = clamp(0.082 + Math.max(0.0, -resolvedNetCash) * 0.035 + Math.max(0.0, resolvedVolatility - 0.20) * 0.045, 0.075, 0.13);
        double resolvedTerminalGrowth = clamp(0.020 + Math.max(-0.02, Math.min(resolvedRevenueGrowth, 0.10)) * 0.12, 0.015, 0.032);
        boolean resolvedPositiveFcf = base.positiveFreeCashFlow() || freeCashFlow(profile) > 0;

        StockFundamentals merged = new StockFundamentals(
                resolvedRevenueGrowth,
                resolvedFcfMargin,
                resolvedWacc,
                resolvedTerminalGrowth,
                resolvedRoe,
                resolvedPe,
                resolvedPb,
                resolvedEvEbitda,
                base.dividendYield(),
                base.buybackYield(),
                resolvedNetCash,
                base.policySensitivity(),
                base.themePremium(),
                resolvedLiquidity,
                base.ahPremiumGap(),
                resolvedSbcRatio,
                resolvedVolatility,
                resolvedFreshness,
                resolvedCompleteness,
                resolvedAnalystCoverage,
                base.chinaExposure(),
                resolvedGovernance,
                resolvedPositiveFcf
        );

        String resolvedName = profile != null && profile.companyName() != null && !profile.companyName().isBlank()
                ? profile.companyName()
                : template.companyName();
        String resolvedIndustry = profile != null && profile.industry() != null && !profile.industry().isBlank()
                ? profile.industry()
                : template.industry();

        return new StockSnapshot(
                Market.US,
                profile != null && profile.primaryTicker() != null && !profile.primaryTicker().isBlank()
                        ? profile.primaryTicker()
                        : template.symbol(),
                "USD",
                resolvedName,
                resolvedIndustry,
                round(resolvedPrice),
                merged,
                usDataVersion(template.dataVersion(), quote, profile)
        );
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

    private double positiveOrDefault(Double value, double fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private double ratioOrDefault(Double value, double fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value > 1 ? value / 100.0 : value;
    }

    private double marketCapScore(Double marketCap, double fallback) {
        if (marketCap == null || marketCap <= 0) {
            return fallback;
        }
        return clamp((Math.log10(marketCap) - 8.8) / 3.0, 0.25, 1.0);
    }

    private double completenessScore(Double pe, Double pb, Double roe, Double turnoverRate, double fallback) {
        double score = 0.62;
        score += pe != null && pe > 0 ? 0.10 : 0.0;
        score += pb != null && pb > 0 ? 0.10 : 0.0;
        score += roe != null && roe > 0 ? 0.10 : 0.0;
        score += turnoverRate != null && turnoverRate > 0 ? 0.06 : 0.0;
        return clamp(Math.max(score, fallback * 0.75), 0.55, 0.96);
    }

    private double impliedEvEbitda(double pe) {
        return pe <= 0 ? 8.0 : pe * 0.55;
    }

    private double resolveUsMarketCap(double price, UsSecClient.UsSecProfile profile, StockFundamentals fallback) {
        if (profile != null && profile.sharesOutstanding() != null && profile.sharesOutstanding() > 0 && price > 0) {
            return price * profile.sharesOutstanding();
        }
        if (profile != null && profile.annualNetIncome() != null && profile.annualNetIncome() > 0 && fallback.pe() > 0) {
            return profile.annualNetIncome() * fallback.pe();
        }
        if (profile != null && profile.annualRevenue() != null && profile.annualRevenue() > 0 && fallback.fcfMargin() > 0.02 && fallback.pe() > 0) {
            double ownerEarnings = profile.annualRevenue() * fallback.fcfMargin();
            return ownerEarnings * Math.max(fallback.pe() * 0.85, 10.0);
        }
        return 0.0;
    }

    private double revenueGrowth(UsSecClient.UsSecProfile profile, double fallback) {
        if (profile == null) {
            return fallback;
        }
        Double current = profile.annualRevenue();
        Double previous = profile.previousAnnualRevenue();
        Double ratio = ratio(current == null || previous == null ? null : current - previous, previous);
        return clamp(orFallback(ratio, fallback), -0.25, 0.55);
    }

    private double freeCashFlowMargin(UsSecClient.UsSecProfile profile, double fallback) {
        if (profile == null) {
            return fallback;
        }
        Double margin = ratio(freeCashFlow(profile), profile.annualRevenue());
        return orFallback(margin, fallback);
    }

    private double freeCashFlow(UsSecClient.UsSecProfile profile) {
        if (profile == null || profile.annualOperatingCashFlow() == null || profile.annualCapex() == null) {
            return Double.NaN;
        }
        return profile.annualOperatingCashFlow() - profile.annualCapex();
    }

    private double resolveUsPe(double marketCap, UsSecClient.UsSecProfile profile, double fallback) {
        if (profile != null && profile.annualNetIncome() != null) {
            if (marketCap > 0 && profile.annualNetIncome() > 0) {
                return clamp(marketCap / profile.annualNetIncome(), 4.0, 95.0);
            }
            if (profile.annualNetIncome() <= 0) {
                return Math.max(fallback, 45.0);
            }
        }
        return fallback;
    }

    private double resolveUsEvEbitda(double marketCap, UsSecClient.UsSecProfile profile, double fallback, double fallbackPe) {
        if (profile != null && profile.ebitdaProxy() != null && profile.ebitdaProxy() > 0 && marketCap > 0) {
            double cash = profile.cash() == null ? 0.0 : profile.cash();
            double debt = profile.totalDebt() == null ? 0.0 : profile.totalDebt();
            double enterpriseValue = marketCap + debt - cash;
            if (enterpriseValue > 0) {
                return clamp(enterpriseValue / profile.ebitdaProxy(), 4.0, 40.0);
            }
        }
        return clamp(fallback * 0.55 + impliedEvEbitda(fallbackPe) * 0.45, 4.0, 40.0);
    }

    private Double netCashToMarketCap(double marketCap, UsSecClient.UsSecProfile profile) {
        if (profile == null || marketCap <= 0) {
            return null;
        }
        double cash = profile.cash() == null ? 0.0 : profile.cash();
        double debt = profile.totalDebt() == null ? 0.0 : profile.totalDebt();
        return (cash - debt) / marketCap;
    }

    private double usFreshnessDays(UsSecClient.UsSecProfile profile, double fallback) {
        if (profile == null) {
            return fallback;
        }
        LocalDate anchor = profile.latest10qDate() != null ? profile.latest10qDate() : profile.latest10kDate();
        if (anchor == null) {
            return fallback;
        }
        return clamp(ChronoUnit.DAYS.between(anchor, LocalDate.now()), 1.0, 400.0);
    }

    private double volumeScore(Long volume, double fallback) {
        if (volume == null || volume <= 0) {
            return fallback;
        }
        return clamp((Math.log10(volume) - 5.0) / 3.0, 0.20, 1.0);
    }

    private String usDataVersion(
            String fallback,
            UsStooqClient.UsQuote quote,
            UsSecClient.UsSecProfile profile
    ) {
        List<String> versions = new ArrayList<>();
        if (quote != null && quote.tradeDate() != null) {
            versions.add("stooq:" + quote.tradeDate());
        }
        if (profile != null) {
            LocalDate anchor = profile.latest10qDate() != null ? profile.latest10qDate() : profile.latest10kDate();
            versions.add("sec:" + (anchor == null ? LocalDate.now() : anchor));
        }
        return versions.isEmpty() ? fallback : String.join("|", versions);
    }

    private Double ratio(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || denominator == 0.0) {
            return null;
        }
        return numerator / denominator;
    }

    private double orFallback(Double value, double fallback) {
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
