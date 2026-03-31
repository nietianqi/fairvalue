package com.fairvalue.engine.us;

public record UsSecurityIdentifier(
        Long id,
        Long securityId,
        String sourceName,
        String sourceSymbol,
        String cik,
        String exchangeCode,
        boolean primary
) {
}
