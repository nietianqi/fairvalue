package com.fairvalue.engine.valuation.strategy;

public final class MathSupport {
    private MathSupport() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
