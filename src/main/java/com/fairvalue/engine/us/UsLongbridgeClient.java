package com.fairvalue.engine.us;

import com.longbridge.Config;
import com.longbridge.quote.AdjustType;
import com.longbridge.quote.CalcIndex;
import com.longbridge.quote.Candlestick;
import com.longbridge.quote.Period;
import com.longbridge.quote.QuoteContext;
import com.longbridge.quote.SecurityCalcIndex;
import com.longbridge.quote.SecurityQuote;
import com.longbridge.quote.SecurityStaticInfo;
import com.longbridge.quote.TradeSessions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class UsLongbridgeClient {
    private static final CalcIndex[] DEFAULT_CALC_INDEXES = {
            CalcIndex.ChangeRate,
            CalcIndex.TotalMarketValue,
            CalcIndex.PeTtmRatio,
            CalcIndex.PbRatio,
            CalcIndex.DividendRatioTtm,
            CalcIndex.TurnoverRate
    };

    private final UsLongbridgeProperties properties;
    private final UsSecurityMasterService usSecurityMasterService;
    private final Map<String, CacheEntry<Optional<UsLongbridgeMarketData>>> marketCache = new ConcurrentHashMap<>();
    private final Object contextLock = new Object();
    private volatile Config config;
    private volatile QuoteContext quoteContext;

    public UsLongbridgeClient(
            UsLongbridgeProperties properties,
            UsSecurityMasterService usSecurityMasterService
    ) {
        this.properties = properties;
        this.usSecurityMasterService = usSecurityMasterService;
    }

    public Optional<UsLongbridgeMarketData> fetchMarketData(String rawTicker) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }

        String symbolFull = normalizeSymbolFull(rawTicker);
        CacheEntry<Optional<UsLongbridgeMarketData>> cached = marketCache.get(symbolFull);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        Optional<UsLongbridgeMarketData> loaded = loadMarketData(symbolFull);
        marketCache.put(
                symbolFull,
                new CacheEntry<>(loaded, now.plus(Duration.ofSeconds(properties.getQuoteCacheTtlSeconds())))
        );
        return loaded;
    }

    private Optional<UsLongbridgeMarketData> loadMarketData(String symbolFull) {
        try {
            QuoteContext context = getQuoteContext();
            String[] symbols = {symbolFull};
            SecurityQuote quote = first(context.getQuote(symbols).get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS));
            SecurityStaticInfo staticInfo = first(context.getStaticInfo(symbols).get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS));
            SecurityCalcIndex calcIndex = first(context.getCalcIndexes(symbols, DEFAULT_CALC_INDEXES).get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS));
            Candlestick[] candlesticks = context.getHistoryCandlesticksByOffset(
                            symbolFull,
                            Period.Day,
                            AdjustType.NoAdjust,
                            false,
                            LocalDateTime.now(ZoneOffset.UTC),
                            properties.getHistoryBars(),
                            TradeSessions.All
                    )
                    .get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);

            if (quote == null && calcIndex == null && staticInfo == null) {
                return Optional.empty();
            }

            List<UsLongbridgeDailyBar> dailyBars = toDailyBars(candlesticks);
            OffsetDateTime timestamp = quote == null ? null : quote.getTimestamp();
            LocalDate tradeDate = timestamp != null
                    ? timestamp.toLocalDate()
                    : !dailyBars.isEmpty() ? dailyBars.get(0).tradeDate() : null;

            return Optional.of(new UsLongbridgeMarketData(
                    symbolFull,
                    normalizeTicker(symbolFull),
                    firstNonBlank(
                            staticInfo == null ? null : staticInfo.getNameEn(),
                            staticInfo == null ? null : staticInfo.getNameCn(),
                            staticInfo == null ? null : staticInfo.getNameHk()
                    ),
                    staticInfo == null ? null : staticInfo.getExchange(),
                    staticInfo == null ? null : staticInfo.getCurrency(),
                    decimal(quote == null ? null : quote.getLastDone()),
                    decimal(quote == null ? null : quote.getPrevClose()),
                    decimal(quote == null ? null : quote.getOpen()),
                    decimal(quote == null ? null : quote.getHigh()),
                    decimal(quote == null ? null : quote.getLow()),
                    quote == null ? 0L : quote.getVolume(),
                    decimal(quote == null ? null : quote.getTurnover()),
                    normalizeRatio(decimal(calcIndex == null ? null : calcIndex.getChangeRate())),
                    decimal(calcIndex == null ? null : calcIndex.getTotalMarketValue()),
                    decimal(calcIndex == null ? null : calcIndex.getPeTtmRatio()),
                    decimal(calcIndex == null ? null : calcIndex.getPbRatio()),
                    firstPositive(
                            normalizeRatio(decimal(calcIndex == null ? null : calcIndex.getDividendRatioTtm())),
                            normalizeRatio(decimal(staticInfo == null ? null : staticInfo.getDividendYield()))
                    ),
                    staticInfo == null ? 0L : staticInfo.getTotalShares(),
                    staticInfo == null ? 0L : staticInfo.getCirculatingShares(),
                    firstPositive(
                            decimal(staticInfo == null ? null : staticInfo.getEpsTtm()),
                            decimal(staticInfo == null ? null : staticInfo.getEps())
                    ),
                    decimal(staticInfo == null ? null : staticInfo.getBps()),
                    tradeDate,
                    "longbridge:" + (tradeDate == null ? LocalDate.now() : tradeDate),
                    dailyBars
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private QuoteContext getQuoteContext() throws Exception {
        QuoteContext current = quoteContext;
        if (current != null) {
            return current;
        }
        synchronized (contextLock) {
            if (quoteContext != null) {
                return quoteContext;
            }
            Config created = Config.fromApikey(
                    properties.getAppKey(),
                    properties.getAppSecret(),
                    properties.getAccessToken()
            );
            if (properties.getHttpUrl() != null && !properties.getHttpUrl().isBlank()) {
                created.httpUrl(properties.getHttpUrl());
            }
            if (properties.getQuoteWebsocketUrl() != null && !properties.getQuoteWebsocketUrl().isBlank()) {
                created.quoteWebsocketUrl(properties.getQuoteWebsocketUrl());
            }
            created.disablePrintQuotePackages();
            quoteContext = QuoteContext.create(created)
                    .get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
            config = created;
            return quoteContext;
        }
    }

    private List<UsLongbridgeDailyBar> toDailyBars(Candlestick[] candlesticks) {
        if (candlesticks == null || candlesticks.length == 0) {
            return List.of();
        }
        List<UsLongbridgeDailyBar> bars = new ArrayList<>(candlesticks.length);
        for (Candlestick candlestick : candlesticks) {
            if (candlestick == null || candlestick.getTimestamp() == null) {
                continue;
            }
            bars.add(new UsLongbridgeDailyBar(
                    candlestick.getTimestamp().toLocalDate(),
                    decimal(candlestick.getOpen()),
                    decimal(candlestick.getHigh()),
                    decimal(candlestick.getLow()),
                    decimal(candlestick.getClose()),
                    candlestick.getVolume(),
                    decimal(candlestick.getTurnover())
            ));
        }
        bars.sort(Comparator.comparing(UsLongbridgeDailyBar::tradeDate).reversed());
        return bars;
    }

    private <T> T first(T[] array) {
        return array == null || array.length == 0 ? null : array[0];
    }

    private double decimal(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double firstPositive(double first, double second) {
        if (first > 0.0) {
            return first;
        }
        return Math.max(second, 0.0);
    }

    private double normalizeRatio(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.abs(value) > 1.0 ? value / 100.0 : value;
    }

    private String normalizeSymbolFull(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            return "";
        }
        String trimmed = rawTicker.trim().toUpperCase(Locale.ROOT).replace(".", "-");
        if (trimmed.endsWith(".US")) {
            return trimmed;
        }
        return usSecurityMasterService.findByTicker(trimmed)
                .map(UsSecurityMaster::symbolFull)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .orElse(trimmed + ".US");
    }

    private String normalizeTicker(String symbolFull) {
        if (symbolFull == null || symbolFull.isBlank()) {
            return "";
        }
        return symbolFull.toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    public record UsLongbridgeDailyBar(
            LocalDate tradeDate,
            double open,
            double high,
            double low,
            double close,
            long volume,
            double turnover
    ) {
    }

    public record UsLongbridgeMarketData(
            String symbolFull,
            String ticker,
            String companyName,
            String exchange,
            String currency,
            double lastDone,
            double prevClose,
            double open,
            double high,
            double low,
            long volume,
            double turnover,
            double changeRate,
            double totalMarketValue,
            double peTtmRatio,
            double pbRatio,
            double dividendYield,
            long totalShares,
            long circulatingShares,
            double epsTtm,
            double bps,
            LocalDate tradeDate,
            String dataVersion,
            List<UsLongbridgeDailyBar> dailyBars
    ) {
        public boolean hasQuote() {
            return lastDone > 0.0;
        }
    }
}
