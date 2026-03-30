package com.fairvalue.engine.domain;

public enum ValuationStatus {
    DEEP_UNDERVALUE,
    SLIGHTLY_UNDERVALUE,
    FAIR,
    SLIGHTLY_OVERVALUE,
    DEEP_OVERVALUE;

    public static ValuationStatus fromUpside(double upside) {
        if (upside >= 0.20) {
            return DEEP_UNDERVALUE;
        }
        if (upside >= 0.05) {
            return SLIGHTLY_UNDERVALUE;
        }
        if (upside <= -0.20) {
            return DEEP_OVERVALUE;
        }
        if (upside <= -0.05) {
            return SLIGHTLY_OVERVALUE;
        }
        return FAIR;
    }
}
