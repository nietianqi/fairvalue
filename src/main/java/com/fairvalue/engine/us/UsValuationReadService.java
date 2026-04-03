package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairvalue.engine.api.dto.us.UsValuationReportResponse;
import com.fairvalue.engine.api.dto.us.UsValuationSummaryResponse;
import com.fairvalue.engine.repository.ValuationLatestSnapshotRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsValuationReadService {
    private final ValuationLatestSnapshotRepository valuationLatestSnapshotRepository;
    private final ObjectMapper objectMapper;

    public UsValuationReadService(
            ValuationLatestSnapshotRepository valuationLatestSnapshotRepository,
            ObjectMapper objectMapper
    ) {
        this.valuationLatestSnapshotRepository = valuationLatestSnapshotRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<UsValuationSummaryResponse> findLatestSummary(String ticker) {
        return valuationLatestSnapshotRepository.findByTicker(normalizeTicker(ticker))
                .flatMap(record -> read(record.summaryJson(), UsValuationSummaryResponse.class));
    }

    public Optional<UsValuationReportResponse> findLatestReport(String ticker) {
        return valuationLatestSnapshotRepository.findByTicker(normalizeTicker(ticker))
                .flatMap(record -> read(record.reportJson(), UsValuationReportResponse.class));
    }

    public Optional<UsStoredValuationSnapshotRecord> findLatestSnapshot(String ticker) {
        return valuationLatestSnapshotRepository.findByTicker(normalizeTicker(ticker));
    }

    private <T> Optional<T> read(String rawJson, Class<T> type) {
        if (rawJson == null || rawJson.isBlank() || "{}".equals(rawJson.trim())) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(rawJson, type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.trim().toUpperCase().replace(".US", "").replace(".", "-");
    }
}
