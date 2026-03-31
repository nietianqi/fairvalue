package com.fairvalue.engine.us;

public record UsSecurityMaster(
        Long id,
        String ticker,
        String symbolFull,
        String companyName,
        String exchange,
        String currency,
        String sector,
        String industry,
        String subindustry,
        String companyType,
        String sectorTemplate,
        String country,
        boolean active
) {
}
