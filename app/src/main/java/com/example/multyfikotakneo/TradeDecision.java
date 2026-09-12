package com.example.multyfikotakneo;

import java.util.UUID;

public final class TradeDecision {
    private TradeDecision() {}

    public static TradeTicket build(Signal s, double ltp, double capital, double utilizationPct,
                                    double tolerancePct, String exchange) {
        if (capital <= 0) throw new IllegalArgumentException("Capital must be above zero.");
        if (utilizationPct <= 0 || utilizationPct > 100) throw new IllegalArgumentException("Capital utilization must be 1–100%.");
        if (tolerancePct < 0 || tolerancePct > 5) throw new IllegalArgumentException("Tolerance must be 0–5%.");
        if (ltp <= 0) throw new IllegalArgumentException("Kotak Neo LTP is invalid.");

        double tol = tolerancePct / 100.0;
        double lowOk = s.entryLow * (1 - tol);
        double highOk = s.entryHigh * (1 + tol);
        String orderType;
        Double orderPrice = null;
        String reason;
        double quantityPrice;

        if ("BUY".equals(s.side)) {
            if (ltp >= lowOk && ltp <= highOk) {
                orderType = "MARKET";
                reason = "LTP is within the advisory entry range/tolerance.";
                quantityPrice = ltp * 1.002; // small buffer for a market BUY ticket
            } else if (ltp > highOk) {
                orderType = "LIMIT";
                orderPrice = s.entryHigh;
                reason = "LTP moved above the advisory BUY entry; do not chase the price.";
                quantityPrice = s.entryHigh;
            } else {
                throw new IllegalArgumentException(String.format(
                        "LTP ₹%.2f is materially below the advisory BUY range. Manual review required.", ltp));
            }
        } else {
            if (ltp >= lowOk && ltp <= highOk) {
                orderType = "MARKET";
                reason = "LTP is within the advisory entry range/tolerance.";
                quantityPrice = ltp;
            } else if (ltp < lowOk) {
                orderType = "LIMIT";
                orderPrice = s.entryLow;
                reason = "LTP moved below the advisory SELL entry; prepare LIMIT at advisory minimum.";
                quantityPrice = s.entryLow;
            } else {
                throw new IllegalArgumentException(String.format(
                        "LTP ₹%.2f is materially above the advisory SELL range. Manual review required.", ltp));
            }
        }

        int quantity = (int) Math.floor((capital * (utilizationPct / 100.0)) / quantityPrice);
        if (quantity < 1) throw new IllegalArgumentException("Capital is too small for one share at the prepared price.");

        TradeTicket t = new TradeTicket();
        t.id = UUID.randomUUID().toString();
        t.symbol = s.symbol;
        t.side = s.side;
        t.exchange = exchange;
        t.product = "MIS";
        t.advisoryEntry = s.advisoryEntryText();
        t.ltp = ltp;
        t.orderType = orderType;
        t.orderPrice = orderPrice;
        t.quantity = quantity;
        t.capital = capital;
        t.target = s.target;
        t.stopLoss = s.stopLoss;
        t.reason = reason;
        t.ticketKind = "ENTRY";
        t.entryLow = s.entryLow;
        t.entryHigh = s.entryHigh;
        t.utilizationPct = utilizationPct;
        t.tolerancePct = tolerancePct;
        return t;
    }

    public static TradeTicket buildExit(KotakNeoPortfolioClient.Position position, double ltp,
                                            String advisoryReason, String exchange) {
        if (position == null || !position.isOpen()) {
            throw new IllegalArgumentException("No open Kotak Neo MIS position was found.");
        }
        TradeTicket t = new TradeTicket();
        t.id = UUID.randomUUID().toString();
        t.symbol = position.symbol;
        t.originalSide = position.originalSide();
        t.side = position.exitSide();
        t.exchange = exchange;
        t.product = "MIS";
        t.advisoryEntry = position.netPrice > 0
                ? String.format("Kotak Neo MIS avg ₹%.2f", position.netPrice)
                : "Kotak Neo MIS position";
        t.ltp = ltp;
        t.orderType = "MARKET";
        t.orderPrice = null;
        t.quantity = position.remainingQuantity();
        t.capital = 0;
        t.target = null;
        t.stopLoss = null;
        t.reason = advisoryReason == null || advisoryReason.isEmpty()
                ? "Multyfi advisory requested an immediate full exit." : advisoryReason;
        t.ticketKind = "EXIT";
        t.instrumentToken = position.instrumentToken;
        t.tradingSymbol = position.tradingSymbol;
        t.utilizationPct = 100.0;
        t.tolerancePct = 0.0;
        return t;
    }
}
