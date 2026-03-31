package com.fairvalue.engine.us;

import java.time.Instant;
import java.time.LocalDate;

public record UsSourceDocument(
        Long id,
        long securityId,
        long sourceId,
        String documentType,
        String documentTitle,
        LocalDate filingDate,
        Instant acceptedAt,
        LocalDate periodEndDate,
        String accessionNo,
        String documentUrl,
        String localStoragePath,
        String checksum,
        String parsedStatus,
        String parserVersion
) {
}
