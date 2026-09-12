package com.example.multyfikotakneo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads Kotak Neo RMS limits and returns the currently usable account limit.
 *  The v2/v3 backend exposes limits at /quick/user/limits.  "Net" is the
 *  remaining limit after current utilisation in Kotak's RMS response.
 */
public final class KotakNeoLimitsClient {
    private KotakNeoLimitsClient() {}

    public static double fetchAvailableFunds(KotakNeoSession session) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("seg", "ALL");
        form.put("exch", "ALL");
        form.put("prod", "ALL");
        String raw = KotakNeoSessionHttp.postForm(session, "/quick/user/limits", form);
        Object root = new JSONTokener(raw).nextValue();

        String error = findError(root);
        if (!error.isEmpty()) throw new IllegalStateException(error);

        Double net = findNumber(root,
                "Net", "net", "availableMargin", "AvailableMargin",
                "availableCash", "AvailableCash", "cashAvailable", "CashAvailable");
        if (net == null) {
            throw new IllegalStateException("Kotak Neo limits response did not contain an available-funds value. Order blocked for safety.");
        }
        if (net <= 0) {
            throw new IllegalStateException("Kotak Neo shows no usable available funds for a new trade.");
        }
        return net;
    }

    private static Double findNumber(Object node, String... keys) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            for (String key : keys) {
                if (o.has(key)) {
                    Double d = toDouble(o.opt(key));
                    if (d != null) return d;
                }
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                Double d = findNumber(o.opt(it.next()), keys);
                if (d != null) return d;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                Double d = findNumber(a.opt(i), keys);
                if (d != null) return d;
            }
        }
        return null;
    }

    private static Double toDouble(Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        try {
            String s = String.valueOf(v).trim().replace(",", "");
            if (s.isEmpty() || "--".equals(s)) return null;
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findError(Object node) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String stat = o.optString("stat", o.optString("status", ""));
            if ("Not_Ok".equalsIgnoreCase(stat) || "error".equalsIgnoreCase(stat) || "failed".equalsIgnoreCase(stat)) {
                for (String key : new String[]{"errMsg", "message", "errorMessage", "desc"}) {
                    String s = o.optString(key, "").trim();
                    if (!s.isEmpty()) return s;
                }
                return "Kotak Neo limits request failed.";
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String e = findError(o.opt(it.next()));
                if (!e.isEmpty()) return e;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                String e = findError(a.opt(i));
                if (!e.isEmpty()) return e;
            }
        }
        return "";
    }
}
