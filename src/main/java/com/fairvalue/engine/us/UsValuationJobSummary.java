package com.fairvalue.engine.us;

public record UsValuationJobSummary(
        long queuedCount,
        long runningCount,
        long completedCount,
        long failedCount,
        long retryableCount,
        long deadLetterCount,
        long readyBacklogCount,
        long staleRunningCount,
        long repeatedFailedCount,
        long totalCount
) {
}
