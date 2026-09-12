package com.example.multyfikotakneo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Locale;

public final class KotakNeoOrderClient {
    private KotakNeoOrderClient() {}

    public static boolean hasActiveExitOrder(KotakNeoSession session, String requestedSymbol, String exitSide) throws Exception {
        String symbol = normalize(requestedSymbol);
        Object root = new JSONTokener(KotakNeoSessionHttp.get(session, "/quick/user/orders")).nextValue();
        JSONArray orders = findArray(root);
        if (orders == null) return false;
        String wantedSide = "BUY".equalsIgnoreCase(exitSide) ? "B" : "S";

        for (int i = 0; i < orders.length(); i++) {
            JSONObject o = orders.optJSONObject(i);
            if (o == null) continue;
            if (!"MIS".equalsIgnoreCase(o.optString("prod", o.optString("product", "")))) continue;
            String ex = o.optString("exSeg", o.optString("exchangeSegment", "nse_cm"));
            if (!"nse_cm".equalsIgnoreCase(ex)) continue;
            if (!matchesSymbol(o, symbol)) continue;
            String side = o.optString("trnsTp", o.optString("transactionType", "")).trim().toUpperCase(Locale.ROOT);
            if ("BUY".equals(side)) side = "B";
            if ("SELL".equals(side)) side = "S";
            if (!wantedSide.equals(side)) continue;
            if (isActive(o.optString("ordSt", o.optString("orderStatus", "")), asInt(o.opt("unFldSz"), -1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSymbol(JSONObject o, String requested) {
        String sym = normalize(o.optString("sym", o.optString("symbol", "")));
        String trd = normalize(o.optString("trdSym", o.optString("tradingSymbol", ""))
                .replaceFirst("(?i)-EQ$", ""));
        return requested.equals(sym) || requested.equals(trd);
    }

    private static boolean isActive(String status, int unfilled) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (s.contains("complete") || s.contains("traded") || s.contains("filled") ||
                s.contains("cancel") || s.contains("reject") || s.contains("fail")) return false;
        if (unfilled > 0) return true;
        return s.contains("open") || s.contains("pending") || s.contains("trigger") || s.contains("received") || s.contains("validation");
    }

    private static JSONArray findArray(Object root) {
        if (root instanceof JSONArray) return (JSONArray) root;
        if (!(root instanceof JSONObject)) return null;
        JSONObject o = (JSONObject) root;
        for (String k : new String[]{"data", "orders", "result", "payload"}) {
            Object v = o.opt(k);
            if (v instanceof JSONArray) return (JSONArray) v;
            if (v instanceof JSONObject) {
                JSONArray nested = findArray(v);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static int asInt(Object v, int fallback) {
        if (v == null || v == JSONObject.NULL) return fallback;
        try { return (int) Math.round(Double.parseDouble(String.valueOf(v).trim())); }
        catch (Exception e) { return fallback; }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
