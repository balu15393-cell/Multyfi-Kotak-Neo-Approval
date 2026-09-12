package com.example.multyfikotakneo;

public final class ExitAlert {
    public final String symbol;
    public final String reason;
    public final String rawText;

    public ExitAlert(String symbol, String reason, String rawText) {
        this.symbol = symbol;
        this.reason = reason;
        this.rawText = rawText;
    }
}
