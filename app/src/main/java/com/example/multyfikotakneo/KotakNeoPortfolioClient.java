package com.example.multyfikotakneo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Locale;

public final class KotakNeoPortfolioClient {
    private KotakNeoPortfolioClient() {}

    public static final class Position {
        public final String symbol;
        public final String tradingSymbol;
        public final String exchangeSegment;
        public final String product;
        public final String instrumentToken;
        public final int quantity;
        public final double netPrice;

        Position(String symbol, String tradingSymbol, String exchangeSegment, String product,
                 String instrumentToken, int quantity, double netPrice) {
            this.symbol = symbol;
            this.tradingSymbol = tradingSymbol;
            this.exchangeSegment = exchangeSegment;
            this.product = product;
            this.instrumentToken = instrumentToken;
            this.quantity = quantity;
            this.netPrice = netPrice;
        }

        public boolean isOpen() { return quantity != 0; }
        public String originalSide() { return quantity > 0 ? "BUY" : "SELL"; }
        public String exitSide() { return quantity > 0 ? "SELL" : "BUY"; }
        public int remainingQuantity() { return Math.abs(quantity); }
    }

    public static Position fetchMisPosition(KotakNeoSession session, String requestedSymbol) throws Exception {
        String symbol = normalize(requestedSymbol);
        Object root = new JSONTokener(KotakNeoSessionHttp.get(session, "/quick/user/positions")).nextValue();
        JSONArray positions = findArray(root);
        if (positions == null) return null;

        int netQty = 0;
        double avgPrice = 0;
        String token = "";
        String tradingSymbol = symbol + "-EQ";
        boolean found = false;

        for (int i = 0; i < positions.length(); i++) {
            JSONObject p = positions.optJSONObject(i);
            if (p == null) continue;
            if (!"MIS".equalsIgnoreCase(p.optString("prod", p.optString("product", "")))) continue;
            String exSeg = p.optString("exSeg", p.optString("exchangeSegment", "nse_cm"));
            if (!"nse_cm".equalsIgnoreCase(exSeg)) continue;
            if (!matchesSymbol(p, symbol)) continue;

            int qty = asInt(p.opt("cfBuyQty")) + asInt(p.opt("flBuyQty"))
                    - asInt(p.opt("cfSellQty")) - asInt(p.opt("flSellQty"));
            if (qty == 0 && p.has("netQty")) qty = asInt(p.opt("netQty"));
            if (qty == 0 && p.has("qty")) qty = asInt(p.opt("qty"));
            netQty += qty;
            found = true;

            String maybeToken = p.optString("tok", p.optString("token", "")).trim();
            if (!maybeToken.isEmpty()) token = maybeToken;
            String ts = p.optString("trdSym", p.optString("tradingSymbol", "")).trim();
            if (!ts.isEmpty()) tradingSymbol = ts;
            double pAvg = firstPositive(p, "avgPrc", "netPrc", "upldPrc", "netPrice");
            if (pAvg > 0) avgPrice = pAvg;
            if (avgPrice <= 0) avgPrice = deriveAverage(p, qty);
        }
        return found ? new Position(symbol, tradingSymbol, "nse_cm", "MIS", token, netQty, avgPrice) : null;
    }

    private static boolean matchesSymbol(JSONObject p, String requested) {
        String sym = normalize(p.optString("sym", p.optString("symbol", "")));
        String trd = normalize(p.optString("trdSym", p.optString("tradingSymbol", ""))
                .replaceFirst("(?i)-EQ$", ""));
        return requested.equals(sym) || requested.equals(trd);
    }

    private static JSONArray findArray(Object root) {
        if (root instanceof JSONArray) return (JSONArray) root;
        if (!(root instanceof JSONObject)) return null;
        JSONObject o = (JSONObject) root;
        for (String k : new String[]{"data", "positions", "result", "payload"}) {
            Object v = o.opt(k);
            if (v instanceof JSONArray) return (JSONArray) v;
            if (v instanceof JSONObject) {
                JSONArray nested = findArray(v);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static int asInt(Object v) {
        if (v == null || v == JSONObject.NULL) return 0;
        try { return (int) Math.round(Double.parseDouble(String.valueOf(v).trim())); }
        catch (Exception e) { return 0; }
    }

    private static double firstPositive(JSONObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k)) continue;
            try {
                double d = Double.parseDouble(String.valueOf(o.opt(k)).trim());
                if (d > 0) return d;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static double deriveAverage(JSONObject p, int qty) {
        try {
            if (qty > 0) {
                double amount = Double.parseDouble(String.valueOf(p.opt("buyAmt")));
                int buyQty = asInt(p.opt("cfBuyQty")) + asInt(p.opt("flBuyQty"));
                return buyQty > 0 ? amount / buyQty : 0;
            } else if (qty < 0) {
                double amount = Double.parseDouble(String.valueOf(p.opt("sellAmt")));
                int sellQty = asInt(p.opt("cfSellQty")) + asInt(p.opt("flSellQty"));
                return sellQty > 0 ? amount / sellQty : 0;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
