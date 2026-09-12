package com.example.multyfikotakneo;

public final class Signal {
    public final String side;
    public final String symbol;
    public final double entryLow;
    public final double entryHigh;
    public final Double target;
    public final Double stopLoss;
    public final String rawText;
    public final boolean intradayDetected;

    public Signal(String side, String symbol, double entryLow, double entryHigh,
                  Double target, Double stopLoss, String rawText, boolean intradayDetected) {
        this.side = side;
        this.symbol = symbol;
        this.entryLow = entryLow;
        this.entryHigh = entryHigh;
        this.target = target;
        this.stopLoss = stopLoss;
        this.rawText = rawText;
        this.intradayDetected = intradayDetected;
    }

    public String advisoryEntryText() {
        if (Math.abs(entryLow - entryHigh) < 0.000001) {
            return String.format("₹%.2f", entryLow);
        }
        return String.format("₹%.2f–₹%.2f", entryLow, entryHigh);
    }
}
