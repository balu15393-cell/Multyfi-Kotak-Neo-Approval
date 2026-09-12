package com.example.multyfikotakneo;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class KotakNeoExecutionClient {
    private KotakNeoExecutionClient() {}

    public static final class Result {
        public final boolean submitted;
        public final boolean noAction;
        public final String orderNumber;
        public final String message;
        public final int quantity;
        public final String side;
        public final String orderType;
        public final Double price;

        Result(boolean submitted, boolean noAction, String orderNumber, String message, int quantity,
               String side, String orderType, Double price) {
            this.submitted = submitted;
            this.noAction = noAction;
            this.orderNumber = orderNumber;
            this.message = message;
            this.quantity = quantity;
            this.side = side;
            this.orderType = orderType;
            this.price = price;
        }
    }

    public static Result execute(Context context, TradeTicket ticket) throws Exception {
        if (ticket == null) throw new IllegalArgumentException("Trade ticket is missing.");
        if (ticket.dryRun) {
            return new Result(false, true, "", "Test ticket only — no Kotak Neo order was sent.",
                    ticket.quantity, ticket.side, ticket.orderType, ticket.orderPrice);
        }
        SettingsRepo settings = new SettingsRepo(context);
        if (!settings.liveExecution()) {
            throw new IllegalStateException("Live API execution is OFF. Open the assistant, enable One-tap Kotak Neo API execution, and save settings.");
        }
        KotakNeoCredentials credentials = new KotakNeoCredentialStore(context).load();
        KotakNeoSession session = new KotakNeoSessionStore(context).load();
        if (credentials == null || !credentials.isComplete()) {
            throw new IllegalStateException("Kotak Neo credentials are missing.");
        }
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API login is required. Log in with current TOTP + MPIN.");
        }

        String token = clean(ticket.instrumentToken);
        String tradingSymbol = clean(ticket.tradingSymbol);
        if (token.isEmpty() || tradingSymbol.isEmpty()) {
            KotakNeoScripResolver.Instrument instrument = new KotakNeoScripResolver(context)
                    .resolveNseEquity(credentials.consumerKey, session, ticket.symbol);
            token = instrument.token;
            tradingSymbol = instrument.tradingSymbol;
        }

        String side;
        int quantity;
        String apiType;
        Double price = null;

        if ("EXIT".equals(ticket.ticketKind)) {
            KotakNeoPortfolioClient.Position position = KotakNeoPortfolioClient.fetchMisPosition(session, ticket.symbol);
            if (position == null || !position.isOpen()) {
                return new Result(false, true, "", "No open Kotak Neo MIS position remains. Nothing was sent.",
                        0, ticket.side, "MKT", null);
            }
            side = position.exitSide();
            quantity = position.remainingQuantity();
            if (KotakNeoOrderClient.hasActiveExitOrder(session, ticket.symbol, side)) {
                return new Result(false, true, "", "An active Kotak Neo exit order already exists. Duplicate exit blocked.",
                        quantity, side, "MKT", null);
            }
            if (!clean(position.instrumentToken).isEmpty()) token = position.instrumentToken;
            if (!clean(position.tradingSymbol).isEmpty()) tradingSymbol = position.tradingSymbol;
            apiType = "MKT";
        } else {
            KotakNeoPortfolioClient.Position existing = KotakNeoPortfolioClient.fetchMisPosition(session, ticket.symbol);
            if (existing != null && existing.isOpen()) {
                throw new IllegalStateException("An open Kotak Neo MIS position already exists for " + ticket.symbol + ". New entry blocked.");
            }
            double ltp = KotakNeoLtpClient.fetch(credentials.consumerKey, session, token);
            ExecutionChoice choice = chooseEntry(ticket, ltp);
            side = ticket.side;
            apiType = choice.orderType;
            price = choice.price;

            // Final funds check at the exact approval tap. Never size above either the
            // user's saved capital cap or Kotak Neo's live available RMS limit.
            double availableFunds = KotakNeoLimitsClient.fetchAvailableFunds(session);
            double effectiveCapital = Math.min(ticket.capital, availableFunds);
            if (effectiveCapital <= 0) {
                throw new IllegalStateException("Kotak Neo shows no usable available funds at approval time.");
            }
            double quantityPrice = price != null ? price : ("BUY".equals(side) ? ltp * 1.002 : ltp);
            quantity = (int) Math.floor((effectiveCapital * (ticket.utilizationPct / 100.0)) / quantityPrice);
            if (quantity < 1) throw new IllegalStateException("Available capital is too small for one share at the latest execution price.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("am", "NO");
        form.put("dq", "0");
        form.put("es", "nse_cm");
        form.put("mp", "0");
        form.put("pc", "MIS");
        form.put("pr", price == null ? "0" : money(price));
        form.put("pt", apiType);
        form.put("qt", Integer.toString(quantity));
        form.put("rt", "DAY");
        form.put("tp", "0");
        form.put("ts", tradingSymbol);
        form.put("tt", "BUY".equalsIgnoreCase(side) ? "B" : "S");
        form.put("ig", "MULTYFI");
        form.put("os", "NEOTRADEAPI");

        String raw;
        try {
            raw = KotakNeoSessionHttp.postForm(session, "/quick/order/rule/ms/place", form);
        } catch (Exception e) {
            String m = safe(e).toLowerCase(Locale.ROOT);
            if (m.contains("unauthorized") || m.contains("session ip") || m.contains("ip doesnt match") || m.contains("ip doesn't match")) {
                throw new IllegalStateException("Kotak Neo rejected live execution because the request/session IP is not the whitelisted static IP. Create the API session and send the order from the same whitelisted static IP.");
            }
            throw e;
        }

        Object root = new JSONTokener(raw).nextValue();
        String error = findError(root);
        if (!error.isEmpty()) throw new IllegalStateException(error);
        String orderNo = findValue(root, "nOrdNo", "orderNo", "orderNumber", "ordNo");
        String msg = orderNo.isEmpty() ? "Kotak Neo accepted the order request." : "Kotak Neo order submitted: " + orderNo;
        return new Result(true, false, orderNo, msg, quantity, side, apiType, price);
    }

    private static final class ExecutionChoice {
        final String orderType; final Double price;
        ExecutionChoice(String orderType, Double price) { this.orderType = orderType; this.price = price; }
    }

    private static ExecutionChoice chooseEntry(TradeTicket t, double ltp) {
        if (ltp <= 0) throw new IllegalStateException("Kotak Neo LTP is invalid at approval time.");
        if (t.entryLow <= 0 || t.entryHigh <= 0) {
            if ("LIMIT".equals(t.orderType) && t.orderPrice != null) return new ExecutionChoice("L", t.orderPrice);
            return new ExecutionChoice("MKT", null);
        }
        double tol = t.tolerancePct / 100.0;
        double lowOk = t.entryLow * (1 - tol);
        double highOk = t.entryHigh * (1 + tol);
        if ("BUY".equals(t.side)) {
            if (ltp >= lowOk && ltp <= highOk) return new ExecutionChoice("MKT", null);
            if (ltp > highOk) return new ExecutionChoice("L", t.entryHigh);
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "Latest LTP ₹%.2f moved materially below the advisory BUY range. Entry blocked for manual review.", ltp));
        } else {
            if (ltp >= lowOk && ltp <= highOk) return new ExecutionChoice("MKT", null);
            if (ltp < lowOk) return new ExecutionChoice("L", t.entryLow);
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "Latest LTP ₹%.2f moved materially above the advisory SELL range. Entry blocked for manual review.", ltp));
        }
    }

    private static String findError(Object root) {
        if (root instanceof JSONObject) {
            JSONObject o = (JSONObject) root;
            String stat = o.optString("stat", o.optString("status", ""));
            if ("Not_Ok".equalsIgnoreCase(stat) || "error".equalsIgnoreCase(stat) || "failed".equalsIgnoreCase(stat)) {
                String m = first(o, "errMsg", "message", "errorMessage", "desc");
                if (!m.isEmpty()) return m;
                Object err = o.opt("error");
                if (err != null && err != JSONObject.NULL) return String.valueOf(err);
                return "Kotak Neo rejected the order.";
            }
            Object err = o.opt("error");
            if (err instanceof JSONArray && ((JSONArray) err).length() > 0) {
                JSONObject e = ((JSONArray) err).optJSONObject(0);
                if (e != null) { String m = first(e, "message", "msg", "description"); if (!m.isEmpty()) return m; }
            }
            for (String k : new String[]{"data", "result", "payload"}) {
                Object v = o.opt(k);
                String nested = findError(v);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static String findValue(Object root, String... keys) {
        if (root instanceof JSONObject) {
            JSONObject o = (JSONObject) root;
            for (String k : keys) { String v = o.optString(k, "").trim(); if (!v.isEmpty()) return v; }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); String v = findValue(o.opt(k), keys); if (!v.isEmpty()) return v; }
        } else if (root instanceof JSONArray) {
            JSONArray a = (JSONArray) root;
            for (int i = 0; i < a.length(); i++) { String v = findValue(a.opt(i), keys); if (!v.isEmpty()) return v; }
        }
        return "";
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k, "").trim(); if (!v.isEmpty()) return v; }
        return "";
    }
    private static String money(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String safe(Exception e) { return e == null || e.getMessage() == null ? "" : e.getMessage(); }
}
