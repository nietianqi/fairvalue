package com.fairvalue.engine.domain;

import java.util.Locale;

public enum Market {
    US,
    CN,
    JP,
    HK;

    public static Market from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Market is required.");
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "US", "USA" -> US;
            case "CN", "CHINA", "A", "ASHARE" -> CN;
            case "JP", "JPN", "JAPAN" -> JP;
            case "HK", "HKG", "HONGKONG" -> HK;
            default -> throw new IllegalArgumentException("Unsupported market: " + raw);
        };
    }
}
