package com.fairvalue.engine.api.dto;

import java.util.List;
import java.util.Map;

public record MarketPeersResponse(
        String market,
        String symbol,
        String selectionBasis,
        String peerSelectionMode,
        String sourceMode,
        String peerSetSource,
        int peerCandidateCount,
        String peerSelectionRuleVersion,
        String peerFilterSummary,
        Map<String, Long> selectionBreakdown,
        List<String> peerFilterMetrics,
        List<MarketPeerItem> items
) {
}
