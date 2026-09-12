package com.example.multyfikotakneo;

import org.json.JSONException;
import org.json.JSONObject;

public final class TradeTicket {
    public String id;
    public String symbol;
    public String side;
    public String exchange;
    public String product;
    public String advisoryEntry;
    public double ltp;
    public String orderType;
    public Double orderPrice;
    public int quantity;
    public double capital;
    public Double target;
    public Double stopLoss;
    public String reason;
    public String ticketKind = "ENTRY";
    public String originalSide;
    public String instrumentToken;
    public String tradingSymbol;
    public double entryLow;
    public double entryHigh;
    public double utilizationPct;
    public double tolerancePct;
    public boolean dryRun;

    public String displayText() {
        String order = "MARKET".equals(orderType) ? "MARKET" : String.format("LIMIT ₹%.2f", orderPrice);
        String ltpText = ltp > 0 ? String.format("Kotak Neo LTP: ₹%.2f\n", ltp) : "Kotak Neo LTP: unavailable\n";
        if ("EXIT".equals(ticketKind)) {
            String positionType = "BUY".equals(originalSide) ? "LONG" : "SHORT";
            return "URGENT MULTYFI EXIT — " + symbol + "\n" +
                    "Current Kotak Neo position: " + positionType + " / MIS\n" +
                    "Exit side: " + side + "\n" +
                    "Exchange: " + exchange + "\n" +
                    "Remaining quantity: " + quantity + "\n" +
                    "Position reference: " + advisoryEntry + "\n" +
                    ltpText +
                    "Prepared exit order: MARKET\n" +
                    "Reason: " + reason + "\n\n" +
                    (dryRun ? "TEST ONLY — no broker order will be sent." : "TAP EXIT NOW TO SEND THIS EXIT THROUGH THE KOTAK NEO API.");
        }
        return symbol + " — " + side + "\n" +
                "Exchange: " + exchange + "\n" +
                "Product: MIS / INTRADAY\n" +
                "Advisory entry: " + advisoryEntry + "\n" +
                ltpText +
                "Order: " + order + "\n" +
                "Quantity: " + quantity + "\n" +
                String.format("Capital cap: ₹%.2f\n", capital) +
                "Target: " + (target == null ? "Not parsed" : String.format("₹%.2f", target)) + "\n" +
                "Stop loss: " + (stopLoss == null ? "Not parsed" : String.format("₹%.2f", stopLoss)) + "\n" +
                "Reason: " + reason + "\n\n" +
                (dryRun ? "TEST ONLY — no broker order will be sent." : "TAP APPROVE & SEND TO SUBMIT THIS ORDER THROUGH THE KOTAK NEO API.");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("symbol", symbol);
        o.put("side", side);
        o.put("exchange", exchange);
        o.put("product", product);
        o.put("advisoryEntry", advisoryEntry);
        o.put("ltp", ltp);
        o.put("orderType", orderType);
        if (orderPrice != null) o.put("orderPrice", orderPrice);
        o.put("quantity", quantity);
        o.put("capital", capital);
        if (target != null) o.put("target", target);
        if (stopLoss != null) o.put("stopLoss", stopLoss);
        o.put("reason", reason);
        o.put("ticketKind", ticketKind == null ? "ENTRY" : ticketKind);
        if (originalSide != null) o.put("originalSide", originalSide);
        if (instrumentToken != null) o.put("instrumentToken", instrumentToken);
        if (tradingSymbol != null) o.put("tradingSymbol", tradingSymbol);
        o.put("entryLow", entryLow);
        o.put("entryHigh", entryHigh);
        o.put("utilizationPct", utilizationPct);
        o.put("tolerancePct", tolerancePct);
        o.put("dryRun", dryRun);
        return o;
    }

    public static TradeTicket fromJson(JSONObject o) throws JSONException {
        TradeTicket t = new TradeTicket();
        t.id = o.getString("id");
        t.symbol = o.getString("symbol");
        t.side = o.getString("side");
        t.exchange = o.getString("exchange");
        t.product = o.getString("product");
        t.advisoryEntry = o.getString("advisoryEntry");
        t.ltp = o.getDouble("ltp");
        t.orderType = o.getString("orderType");
        t.orderPrice = o.has("orderPrice") ? o.getDouble("orderPrice") : null;
        t.quantity = o.getInt("quantity");
        t.capital = o.getDouble("capital");
        t.target = o.has("target") ? o.getDouble("target") : null;
        t.stopLoss = o.has("stopLoss") ? o.getDouble("stopLoss") : null;
        t.reason = o.optString("reason", "");
        t.ticketKind = o.optString("ticketKind", "ENTRY");
        t.originalSide = o.has("originalSide") ? o.optString("originalSide", null) : null;
        t.instrumentToken = o.has("instrumentToken") ? o.optString("instrumentToken", null) : null;
        t.tradingSymbol = o.has("tradingSymbol") ? o.optString("tradingSymbol", null) : null;
        t.entryLow = o.optDouble("entryLow", 0);
        t.entryHigh = o.optDouble("entryHigh", 0);
        t.utilizationPct = o.optDouble("utilizationPct", 99.5);
        t.tolerancePct = o.optDouble("tolerancePct", 0.20);
        t.dryRun = o.optBoolean("dryRun", false);
        return t;
    }
}
