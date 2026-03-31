package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.MarketDataRawRepository;
import com.fairvalue.engine.repository.MarketIntradaySnapshotRepository;
import com.fairvalue.engine.repository.MarketPriceDailyRepository;
import com.fairvalue.engine.repository.MarketSnapshotRepository;
import com.fairvalue.engine.repository.SourceRegistryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UsMarketDataPersistenceService {
    private static final Duration SNAPSHOT_WRITE_INTERVAL = Duration.ofMinutes(10);

    private final UsSecurityMasterService usSecurityMasterService;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final MarketDataRawRepository marketDataRawRepository;
    private final MarketPriceDailyRepository marketPriceDailyRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final MarketIntradaySnapshotRepository marketIntradaySnapshotRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> lastSnapshotWriteAt = new ConcurrentHashMap<>();

    public UsMarketDataPersistenceService(
            UsSecurityMasterService usSecurityMasterService,
            SourceRegistryRepository sourceRegistryRepository,
            MarketDataRawRepository marketDataRawRepository,
            MarketPriceDailyRepository marketPriceDailyRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            MarketIntradaySnapshotRepository marketIntradaySnapshotRepository,
            ObjectMapper objectMapper
    ) {
        this.usSecurityMasterService = usSecurityMasterService;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.marketDataRawRepository = marketDataRawRepository;
        this.marketPriceDailyRepository = marketPriceDailyRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.marketIntradaySnapshotRepository = marketIntradaySnapshotRepository;
        this.objectMapper = objectMapper;
    }

    public void persistLiveSnapshot(
            String rawTicker,
            StockSnapshot snapshot,
            UsStooqClient.UsQuote quote,
            UsSecClient.UsSecProfile profile
    ) {
        if (quote == null || quote.tradeDate() == null || quote.close() <= 0 || snapshot == null) {
            return;
        }

        String ticker = normalizeTicker(rawTicker);
        Long securityId = usSecurityMasterService.resolveSecurityId(ticker).orElse(null);
        Long sourceId = sourceRegistryRepository.findIdBySourceName(UsSecurityIdentifierService.STOOQ).orElse(null);
        if (securityId == null || sourceId == null) {
            return;
        }

        LocalDate tradeDate = quote.tradeDate();
        Instant snapshotTime = Instant.now();
        BigDecimal open = decimal(quote.open());
        BigDecimal high = decimal(quote.high());
        BigDecimal low = decimal(quote.low());
        BigDecimal close = decimal(quote.close());
        BigDecimal prevClose = quote.previousClose() > 0 ? decimal(quote.previousClose()) : null;
        BigDecimal turnover = quote.volume() > 0 ? decimal(quote.close() * quote.volume()) : null;
        BigDecimal marketCap = marketCap(snapshot.price(), profile);
        BigDecimal pe = positive(snapshot.fundamentals().pe());
        BigDecimal pb = positive(snapshot.fundamentals().pb());
        BigDecimal dividendYield = decimal(snapshot.fundamentals().dividendYield());
        BigDecimal epsTtm = pe == null ? null : close.divide(pe, 6, RoundingMode.HALF_UP);
        BigDecimal bps = pb == null ? null : close.divide(pb, 6, RoundingMode.HALF_UP);
        BigDecimal shares = profile != null && profile.sharesOutstanding() != null && profile.sharesOutstanding() > 0
                ? decimal(profile.sharesOutstanding())
                : null;

        marketPriceDailyRepository.upsert(
                securityId,
                tradeDate,
                open,
                high,
                low,
                close,
                quote.volume(),
                turnover,
                close,
                BigDecimal.ONE,
                BigDecimal.ONE,
                sourceId
        );

        if (!shouldWriteSnapshot(ticker, tradeDate, snapshotTime)) {
            return;
        }

        marketDataRawRepository.insert(
                securityId,
                sourceId,
                "snapshot",
                tradeDate,
                snapshotTime,
                toJsonPayload(snapshot, quote, profile)
        );
        marketSnapshotRepository.insert(
                securityId,
                snapshotTime,
                close,
                prevClose,
                open,
                high,
                low,
                quote.volume(),
                turnover,
                marketCap,
                pe,
                pb,
                dividendYield,
                epsTtm,
                bps,
                shares,
                shares,
                sourceId
        );
        marketIntradaySnapshotRepository.insert(
                securityId,
                snapshotTime,
                close,
                quote.volume(),
                turnover,
                decimal(quote.dailyChange()),
                sourceId
        );
        lastSnapshotWriteAt.put(snapshotKey(ticker, tradeDate), snapshotTime);
    }

    private boolean shouldWriteSnapshot(String ticker, LocalDate tradeDate, Instant snapshotTime) {
        Instant lastWrite = lastSnapshotWriteAt.get(snapshotKey(ticker, tradeDate));
        return lastWrite == null || lastWrite.plus(SNAPSHOT_WRITE_INTERVAL).isBefore(snapshotTime);
    }

    private String toJsonPayload(
            StockSnapshot snapshot,
            UsStooqClient.UsQuote quote,
            UsSecClient.UsSecProfile profile
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ticker", snapshot.symbol());
            payload.put("company_name", snapshot.companyName());
            payload.put("industry", snapshot.industry());
            payload.put("data_version", snapshot.dataVersion());
            payload.put("quote_source", UsSecurityIdentifierService.STOOQ);
            payload.put("enrichment_source", profile == null ? null : UsSecurityIdentifierService.SEC_EDGAR);

            Map<String, Object> quotePayload = new LinkedHashMap<>();
            quotePayload.put("trade_date", quote.tradeDate());
            quotePayload.put("open", quote.open());
            quotePayload.put("high", quote.high());
            quotePayload.put("low", quote.low());
            quotePayload.put("close", quote.close());
            quotePayload.put("previous_close", quote.previousClose());
            quotePayload.put("volume", quote.volume());
            quotePayload.put("daily_change", quote.dailyChange());
            payload.put("quote", quotePayload);

            if (profile != null) {
                Map<String, Object> profilePayload = new LinkedHashMap<>();
                profilePayload.put("exchange", profile.exchange());
                profilePayload.put("industry", profile.industry());
                profilePayload.put("shares_outstanding", profile.sharesOutstanding());
                profilePayload.put("annual_revenue", profile.annualRevenue());
                profilePayload.put("annual_net_income", profile.annualNetIncome());
                profilePayload.put("cash", profile.cash());
                profilePayload.put("total_debt", profile.totalDebt());
                payload.put("sec_profile", profilePayload);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize US market data payload.", ex);
        }
    }

    private BigDecimal marketCap(double price, UsSecClient.UsSecProfile profile) {
        if (profile == null || profile.sharesOutstanding() == null || profile.sharesOutstanding() <= 0 || price <= 0) {
            return null;
        }
        return decimal(price * profile.sharesOutstanding());
    }

    private BigDecimal positive(double value) {
        return value > 0 ? decimal(value) : null;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private String snapshotKey(String ticker, LocalDate tradeDate) {
        return ticker + ":" + tradeDate;
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".US", "")
                .replace(".", "-");
    }
}
