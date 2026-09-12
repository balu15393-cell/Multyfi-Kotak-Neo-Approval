package com.example.multyfikotakneo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class KotakNeoLtpClient {
    private KotakNeoLtpClient() {}

    public static double fetch(String consumerKey, KotakNeoSession session, String instrumentToken) throws Exception {
        if (consumerKey == null || consumerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Kotak Neo API token/consumer key is missing.");
        }
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API session is missing. Log in with TOTP + MPIN first.");
        }
        if (instrumentToken == null || instrumentToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Kotak Neo instrument token is missing.");
        }

        String neoSymbol = "nse_cm%7C" + URLEncoder.encode(instrumentToken.trim(), StandardCharsets.UTF_8.name());
        String endpoint = session.baseUrl + "/script-details/1.0/quotes/neosymbol/" + neoSymbol + "/ltp";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", consumerKey.trim());

        int code = conn.getResponseCode();
        String raw = read(conn, code);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(apiError(raw, "Kotak Neo LTP request failed (HTTP " + code + ")."));
        }
        Object root = new JSONTokener(raw).nextValue();
        Double ltp = extractLtp(root);
        if (ltp == null || ltp <= 0) throw new IllegalStateException("Kotak Neo returned an invalid or missing LTP.");
        return ltp;
    }

    private static Double extractLtp(Object value) {
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                Double d = extractLtp(a.opt(i));
                if (d != null) return d;
            }
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            if (o.has("ltp")) {
                double d = parseDouble(o.opt("ltp"), -1);
                if (d > 0) return d;
            }
            String[] keys = {"data", "payload", "quotes", "result"};
            for (String k : keys) {
                if (o.has(k)) {
                    Double d = extractLtp(o.opt(k));
                    if (d != null) return d;
                }
            }
        }
        return null;
    }

    private static double parseDouble(Object v, double fallback) {
        if (v == null || v == JSONObject.NULL) return fallback;
        try { return Double.parseDouble(String.valueOf(v).trim()); }
        catch (Exception e) { return fallback; }
    }

    private static String apiError(String raw, String fallback) {
        try { return KotakNeoAuthClient.errorMessage(new JSONObject(raw), fallback); }
        catch (Exception e) { return fallback; }
    }

    private static String read(HttpURLConnection conn, int code) throws Exception {
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
