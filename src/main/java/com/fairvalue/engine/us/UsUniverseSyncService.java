package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsUniverseSyncService {
    private static final Logger log = LoggerFactory.getLogger(UsUniverseSyncService.class);
    private static final String JOB_TYPE = "us_sec_ticker_universe_sync";

    private final UsSecClient usSecClient;
    private final UsSecurityMasterService securityMasterService;
    private final IngestionJobRepository ingestionJobRepository;
    private final UsLiveMarketDataProperties properties;
    private final ObjectMapper objectMapper;

    public UsUniverseSyncService(
            UsSecClient usSecClient,
            UsSecurityMasterService securityMasterService,
            IngestionJobRepository ingestionJobRepository,
            UsLiveMarketDataProperties properties,
            ObjectMapper objectMapper
    ) {
        this.usSecClient = usSecClient;
        this.securityMasterService = securityMasterService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public UniverseSyncSummary syncSecTickerUniverse() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("US live market data is disabled, SEC universe sync cannot run.");
        }

        Instant startedAt = Instant.now();
        long jobId = ingestionJobRepository.startJob(
                JOB_TYPE,
                toJson(Map.of("source", UsSecurityIdentifierService.SEC_EDGAR, "status", "running"))
        );

        int attempts = 0;
        try {
            List<UsSecClient.SecTickerInfo> universe = List.of();
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= properties.getUniverseSyncMaxRetries(); attempt += 1) {
                attempts = attempt;
                try {
                    universe = usSecClient.fetchTickerUniverse(true);
                    if (universe.isEmpty()) {
                        throw new IllegalStateException("SEC ticker universe returned empty payload.");
                    }
                    lastFailure = null;
                    break;
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    log.warn("SEC ticker universe sync attempt {} failed: {}", attempt, ex.getMessage());
                    sleepBeforeRetry(attempt);
                }
            }

            if (lastFailure != null && attempts >= properties.getUniverseSyncMaxRetries()) {
                throw lastFailure;
            }

            int inserted = 0;
            int updated = 0;
            int failed = 0;
            List<String> failedTickers = new ArrayList<>();
            for (UsSecClient.SecTickerInfo tickerInfo : universe) {
                try {
                    UsSecurityMasterService.UpsertOutcome outcome = securityMasterService.upsertFromSecTicker(tickerInfo);
                    if (outcome.created()) {
                        inserted += 1;
                    } else {
                        updated += 1;
                    }
                } catch (RuntimeException ex) {
                    failed += 1;
                    failedTickers.add(tickerInfo.ticker());
                    log.warn("Failed to sync SEC ticker {} (CIK {}): {}", tickerInfo.ticker(), tickerInfo.cik(), ex.getMessage());
                }
            }

            Instant finishedAt = Instant.now();
            String status = failed == 0 ? "completed" : "completed_with_errors";
            UniverseSyncSummary summary = new UniverseSyncSummary(
                    jobId,
                    UsSecurityIdentifierService.SEC_EDGAR,
                    universe.size(),
                    inserted,
                    updated,
                    failed,
                    attempts,
                    startedAt,
                    finishedAt,
                    status,
                    failedTickers
            );
            ingestionJobRepository.finishJob(
                    jobId,
                    status,
                    attempts - 1,
                    failed == 0 ? null : "Some tickers failed to sync.",
                    toJson(summary.toPayload())
            );
            return summary;
        } catch (RuntimeException ex) {
            Instant finishedAt = Instant.now();
            UniverseSyncSummary summary = new UniverseSyncSummary(
                    jobId,
                    UsSecurityIdentifierService.SEC_EDGAR,
                    0,
                    0,
                    0,
                    0,
                    attempts,
                    startedAt,
                    finishedAt,
                    "failed",
                    List.of()
            );
            ingestionJobRepository.finishJob(
                    jobId,
                    "failed",
                    Math.max(attempts - 1, 0),
                    ex.getMessage(),
                    toJson(summary.toPayloadWithError(ex.getMessage()))
            );
            throw ex;
        }
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= properties.getUniverseSyncMaxRetries()) {
            return;
        }
        try {
            Thread.sleep(properties.getUniverseSyncRetryBackoffMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying SEC ticker universe sync.", ex);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize ingestion payload.", ex);
        }
    }

    public record UniverseSyncSummary(
            long jobId,
            String sourceName,
            int totalFetched,
            int inserted,
            int updated,
            int failed,
            int attempts,
            Instant startedAt,
            Instant finishedAt,
            String status,
            List<String> failedTickers
    ) {
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("job_id", jobId);
            payload.put("source_name", sourceName);
            payload.put("total_fetched", totalFetched);
            payload.put("inserted", inserted);
            payload.put("updated", updated);
            payload.put("failed", failed);
            payload.put("attempts", attempts);
            payload.put("started_at", startedAt.toString());
            payload.put("finished_at", finishedAt.toString());
            payload.put("status", status);
            payload.put("failed_tickers", failedTickers);
            return payload;
        }

        public Map<String, Object> toPayloadWithError(String errorMessage) {
            Map<String, Object> payload = new LinkedHashMap<>(toPayload());
            payload.put("error_message", errorMessage);
            return payload;
        }
    }
}
